package cn.programcx.foxnaserver.security;

import cn.programcx.foxnaserver.api.auth.TokenStorageService;
import cn.programcx.foxnaserver.dto.auth.OAuthBindState;
import cn.programcx.foxnaserver.entity.User;
import cn.programcx.foxnaserver.entity.UserOAuth;
import cn.programcx.foxnaserver.service.auth.AuthenticationService;
import cn.programcx.foxnaserver.service.auth.OAuthBindStateService;
import cn.programcx.foxnaserver.service.auth.impl.UserOAuthServiceImpl;
import cn.programcx.foxnaserver.service.user.UserService;
import cn.programcx.foxnaserver.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Slf4j
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
    @Lazy
    @Autowired
    private UserOAuthServiceImpl userOAuthService;

    @Value("${oauth.frontend.base.url}")
    private String frontendBaseUrl;

    @Lazy
    @Autowired
    private TokenStorageService tokenStorageService;

    @Lazy
    @Autowired
    private UserService userService;

    @Lazy
    @Autowired
    private JwtUtil jwtUtil;

    @Lazy
    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserDetailsService userDetailsService;

    @Lazy
    @Autowired
    private OAuthBindStateService oAuthBindStateService;

    // ==================== 内部辅助记录类 ====================

    /**
     * 封装从第三方 OAuth 提供者提取的用户信息
     */
    private record OAuthUserInfo(String oauthId, String username, String email) {
    }

    /**
     * 封装 OAuth 绑定上下文（todo、目标用户名、重定向URL）
     */
    private static class BindContext {
        String todo = "";
        String usernameToBind = "";
        String redirectUrl = "";

        boolean isBindRequest() {
            return !usernameToBind.isEmpty();
        }
    }

    // ==================== 主方法 ====================

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2AuthenticationToken auth = (OAuth2AuthenticationToken) authentication;
        String registrationId = auth.getAuthorizedClientRegistrationId();

        // 1. 解析绑定上下文
        BindContext bindContext = resolveBindContext(request);

        log.info("OAuth2 登录成功，来源: {}, todo: {}, usernameToBind: {}, redirectUrl: {}",
                registrationId, bindContext.todo, bindContext.usernameToBind, bindContext.redirectUrl);

        // 2. 提取第三方用户信息
        OAuthUserInfo userInfo = extractOAuthUserInfo(auth);

        // 3. 查找本地 OAuth 关联记录
        UserOAuth userOAuth = userOAuthService.findOAuthUser(registrationId, userInfo.oauthId());

        if (userOAuth == null) {
            handleNewOAuthUser(response, registrationId, userInfo, bindContext);
        } else {
            handleExistingOAuthUser(response, registrationId, userInfo, userOAuth);
        }
    }

    // ==================== 绑定上下文解析 ====================

    /**
     * 从 Cookie 和 Redis 中解析 OAuth 绑定上下文，兼容旧版 URL 参数
     */
    private BindContext resolveBindContext(HttpServletRequest request) {
        BindContext ctx = new BindContext();

        // 优先从 Cookie + Redis 获取
        String ourState = extractStateFromCookies(request);
        if (ourState != null && !ourState.isEmpty()) {
            OAuthBindState bindState = oAuthBindStateService.getAndRemoveBindState(ourState);
            if (bindState != null) {
                ctx.todo = bindState.getTodo();
                ctx.redirectUrl = bindState.getRedirectUrl();
                log.info("Retrieved OAuth bind state from Redis: ourState={}, todo={}, redirectUrl={}",
                        ourState, ctx.todo, ctx.redirectUrl);
            } else {
                log.warn("OAuth bind state not found or expired in Redis: ourState={}", ourState);
            }
        }

        // 从 todo 中提取待绑定的用户名
        if (ctx.todo != null && ctx.todo.startsWith("BIND_")) {
            ctx.usernameToBind = ctx.todo.substring("BIND_".length());
        }

        // 兼容旧版本：回退到 URL 参数
        if (ctx.todo.isEmpty()) {
            String stateParam = getParamOrEmpty(request, "state");
            if (stateParam.startsWith("BIND_")) {
                ctx.todo = stateParam;
                ctx.usernameToBind = stateParam.substring("BIND_".length());
                log.info("Using legacy state parameter: todo={}", ctx.todo);
            }
        }
        if (ctx.redirectUrl.isEmpty()) {
            ctx.redirectUrl = getParamOrEmpty(request, "redirectUrl");
        }

        return ctx;
    }

    /**
     * 从请求 Cookie 中提取 OAUTH_BIND_STATE 值
     */
    private String extractStateFromCookies(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("OAUTH_BIND_STATE".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    // ==================== OAuth 用户信息提取 ====================

    /**
     * 根据不同的 OAuth 提供者提取用户信息（oauthId、username、email）
     */
    private OAuthUserInfo extractOAuthUserInfo(OAuth2AuthenticationToken auth) {
        OAuth2User oAuth2User = auth.getPrincipal();
        String registrationId = auth.getAuthorizedClientRegistrationId();

        return switch (registrationId) {
            case "github" -> extractGithubUserInfo(oAuth2User);
            case "qq" -> extractQqUserInfo(oAuth2User);
            case "microsoft" -> extractMicrosoftUserInfo(oAuth2User);
            default -> throw new IllegalStateException("Unsupported registration id: " + registrationId);
        };
    }

    private OAuthUserInfo extractGithubUserInfo(OAuth2User oAuth2User) {
        String oauthId = Objects.requireNonNull(oAuth2User.getAttribute("id")).toString();
        String username = Objects.requireNonNull(oAuth2User.getAttribute("login")).toString();
        String email = getStringAttribute(oAuth2User, "email");
        return new OAuthUserInfo(oauthId, username, email);
    }

    private OAuthUserInfo extractQqUserInfo(OAuth2User oAuth2User) {
        String oauthId = Objects.requireNonNull(oAuth2User.getAttribute("openid")).toString();
        String username = Objects.requireNonNull(oAuth2User.getAttribute("nickname")).toString();
        String email = getStringAttribute(oAuth2User, "email");
        return new OAuthUserInfo(oauthId, username, email);
    }

    private OAuthUserInfo extractMicrosoftUserInfo(OAuth2User oAuth2User) {
        log.debug("Microsoft OAuth user attributes: {}", oAuth2User.getAttributes());

        Object idObj = oAuth2User.getAttribute("sub");
        if (idObj == null) {
            log.error("Microsoft OAuth: Unable to get user id (sub not found). Available attributes: {}",
                    oAuth2User.getAttributes().keySet());
            throw new IllegalStateException("Microsoft OAuth: Unable to get user id (sub not found)");
        }
        String oauthId = idObj.toString();

        Object displayNameObj = oAuth2User.getAttribute("name");
        String username = displayNameObj != null ? displayNameObj.toString() : "MicrosoftUser";

        Object emailObj = oAuth2User.getAttribute("email");
        if (emailObj == null) {
            emailObj = oAuth2User.getAttribute("preferred_username");
        }
        String email = emailObj == null ? null : emailObj.toString();

        log.info("Microsoft OAuth login - id: {}, username: {}, email: {}", oauthId, username, email);
        return new OAuthUserInfo(oauthId, username, email);
    }

    // ==================== 业务处理：新 OAuth 用户 ====================

    /**
     * 处理首次通过 OAuth 登录的用户（本地无关联记录）
     */
    private void handleNewOAuthUser(HttpServletResponse response,
                                    String registrationId,
                                    OAuthUserInfo userInfo,
                                    BindContext bindContext) throws IOException {
        UserOAuth userOAuth = new UserOAuth();
        userOAuth.setProvider(registrationId);
        userOAuth.setOauthId(userInfo.oauthId());

        log.info("尝试绑定第三方账号到现有用户[{}],{}", bindContext.usernameToBind, userInfo.username());

        if (bindContext.isBindRequest()) {
            handleBindToExistingUser(response, registrationId, userOAuth, bindContext);
            return;
        }

        handleRegisterNewUser(response, registrationId, userInfo, userOAuth);
    }

    /**
     * 绑定第三方账号到已有本地用户
     */
    private void handleBindToExistingUser(HttpServletResponse response,
                                          String registrationId,
                                          UserOAuth userOAuth,
                                          BindContext bindContext) throws IOException {
        String usernameToBind = bindContext.usernameToBind;
        userOAuth.setUserName(usernameToBind);

        // 校验是否已绑定
        LambdaQueryWrapper<UserOAuth> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserOAuth::getProvider, registrationId)
                .eq(UserOAuth::getUserName, usernameToBind);

        if (userOAuthService.count(queryWrapper) > 0) {
            log.warn("绑定失败：用户 [{}] 已经绑定过 [{}] 第三方账号", usernameToBind, registrationId);
            if (!bindContext.redirectUrl.isEmpty()) {
                redirectWithParam(response, bindContext.redirectUrl, "error", "已经绑定过该第三方账号");
            }
            return;
        }

        userOAuthService.addOAuthUser(userOAuth);
        log.info("成功绑定第三方账号 [{}] 到用户 [{}]", registrationId, usernameToBind);

        if (!bindContext.redirectUrl.isEmpty()) {
            redirectWithParam(response, bindContext.redirectUrl, "info", "绑定成功");
        }
    }

    /**
     * 注册全新的 OAuth 用户（生成本地账号，待激活）
     */
    private void handleRegisterNewUser(HttpServletResponse response,
                                       String registrationId,
                                       OAuthUserInfo userInfo,
                                       UserOAuth userOAuth) {
        // 防止用户名冲突
        String baseUsername = userOAuthService.generateUsernameByOAuthProvider(registrationId, userInfo.username());
        String databaseUsername = generateUniqueUsername(baseUsername);
        userOAuth.setUserName(databaseUsername);

        User user = new User();
        user.setUserName(databaseUsername);
        user.setEmail(userInfo.email());
        user.setState("pending");

        log.info("注册用户[{}]，邮箱[{}]", user.getUserName(), user.getEmail());
        log.info("OAuth 对象情况：provider={}, oauthId={}, userName={}",
                userOAuth.getProvider(), userOAuth.getOauthId(), userOAuth.getUserName());

        userOAuthService.saveUserUserOAuth(userOAuth, user);

        try {
            String ticket = userOAuthService.generateActivateTicket();
            userOAuthService.saveTicketByProviderOAuthId(registrationId, userInfo.oauthId(), ticket);
            authenticationService.iniPermissionForNewUser(user);
            response.sendRedirect(String.format("%s/oauth/activate?ticket=%s", frontendBaseUrl, ticket));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== 业务处理：已有 OAuth 用户 ====================

    /**
     * 处理已有 OAuth 关联记录的用户登录
     */
    private void handleExistingOAuthUser(HttpServletResponse response,
                                         String registrationId,
                                         OAuthUserInfo userInfo,
                                         UserOAuth userOAuth) throws IOException {
        User user = userService.findUserByUsername(userOAuth.getUserName());

        try {
            authenticationService.checkUserStatus(user.getUserName());
        } catch (Exception e) {
            log.error("用户[{}]状态异常：{}", user.getUserName(), e.getMessage());
            handleAbnormalUserState(response, registrationId, userInfo.oauthId(), user);
            return;
        }

        // 正常登录，签发 Token
        issueTokensAndRedirect(response, userInfo.username(), user);
    }

    /**
     * 处理用户状态异常（禁用 / 待激活）
     */
    private void handleAbnormalUserState(HttpServletResponse response,
                                         String registrationId,
                                         String oauthId,
                                         User user) throws IOException {
        if ("disabled".equals(user.getState())) {
            response.sendRedirect(String.format("%s/oauth/error?message=%s",
                    frontendBaseUrl, URLEncoder.encode("该用户已经被禁止登录！", StandardCharsets.UTF_8)));
        } else if ("pending".equals(user.getState())) {
            try {
                String ticket = userOAuthService.generateActivateTicket();
                userOAuthService.saveTicketByProviderOAuthId(registrationId, oauthId, ticket);
                response.sendRedirect(String.format("%s/oauth/activate?ticket=%s", frontendBaseUrl, ticket));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * 签发 accessToken / refreshToken 并重定向到前端
     */
    private void issueTokensAndRedirect(HttpServletResponse response,
                                         String displayUsername,
                                         User user) throws IOException {
        final UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUserName());

        String accessToken = jwtUtil.generateAccessTokenByUuid(user.getId(), userDetails.getAuthorities());
        String refreshToken = jwtUtil.generateRefreshTokenByUuid(user.getId(), userDetails.getAuthorities());

        tokenStorageService.storeAccessToken(accessToken, user.getId());
        tokenStorageService.storeRefreshToken(refreshToken, user.getId());

        log.info("[{}]用户登录成功！UUID: {}", displayUsername, user.getId());

        String redirectUrl = String.format(
                "%s/oauth/success?accessToken=%s&refreshToken=%s&username=%s&uuid=%s",
                frontendBaseUrl, accessToken, refreshToken, user.getUserName(), user.getId());

        response.sendRedirect(redirectUrl);
    }

    // ==================== 工具方法 ====================

    /**
     * 生成不冲突的唯一用户名
     */
    private String generateUniqueUsername(String baseUsername) {
        String candidate = baseUsername;
        int counter = 1;
        while (userService.findUserByUsername(candidate) != null) {
            candidate = baseUsername + "_" + counter;
            counter++;
        }
        return candidate;
    }

    /**
     * 安全获取请求参数，不存在时返回空字符串
     */
    private String getParamOrEmpty(HttpServletRequest request, String name) {
        String value = request.getParameter(name);
        return value == null ? "" : value;
    }

    /**
     * 安全获取 OAuth2User 的 String 属性
     */
    private String getStringAttribute(OAuth2User oAuth2User, String attributeName) {
        Object obj = oAuth2User.getAttribute(attributeName);
        return obj == null ? null : obj.toString();
    }

    /**
     * 带 URL 编码参数的重定向
     */
    private void redirectWithParam(HttpServletResponse response,
                                   String baseUrl,
                                   String paramName,
                                   String paramValue) throws IOException {
        String encoded = URLEncoder.encode(paramValue, StandardCharsets.UTF_8);
        String separator = baseUrl.contains("?") ? "&" : "?";
        response.sendRedirect(baseUrl + separator + paramName + "=" + encoded);
    }
}
