package cn.programcx.foxnaserver.security;

import cn.programcx.foxnaserver.controller.auth.TokenStorageService;
import cn.programcx.foxnaserver.entity.User;
import cn.programcx.foxnaserver.entity.UserOAuth;
import cn.programcx.foxnaserver.service.auth.AuthenticationService;
import cn.programcx.foxnaserver.service.auth.impl.UserOAuthServiceImpl;
import cn.programcx.foxnaserver.service.user.UserService;
import cn.programcx.foxnaserver.util.JwtUtil;
import jakarta.servlet.ServletException;
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

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        OAuth2AuthenticationToken auth = (OAuth2AuthenticationToken) authentication;

        OAuth2User oAuth2User = auth.getPrincipal();
        String registrationId = auth.getAuthorizedClientRegistrationId();


        // 获取 第三方唯一标识
        String oauthId;
        StringBuilder username;
        String email;

        if("github".equals(registrationId)){
            oauthId = oAuth2User.getAttribute("id").toString();
            username = new StringBuilder(oAuth2User.getAttribute("login").toString());
            Object emailObj = oAuth2User.getAttribute("email");
            email = emailObj == null ? null : emailObj.toString();

        }else {
            throw new IllegalStateException("Unsupported registration id");
        }

        //本地数据库操作
        UserOAuth userOAuth = userOAuthService.findOAuthUser(registrationId, oauthId);
        if(userOAuth == null){

            // 插入OAuth用户和用户实体
            userOAuth = new UserOAuth();
            userOAuth.setProvider(registrationId);
            userOAuth.setOauthId(oauthId);

            // 防止用户名冲突
            String baseUsername = userOAuthService.generateUsernameByOAuthProvider(registrationId,username.toString());
            String databaseUsername = baseUsername;
            int counter = 1;

            while (userService.findUserByUsername(databaseUsername) != null) {
                databaseUsername = baseUsername + "_" + counter;
                counter++;
            }

            userOAuth.setUserName(databaseUsername);

            User user = new User();
            user.setUserName(userOAuth.getUserName());
            user.setEmail(email);

            //设置待验证状态
            user.setState("pending");

            log.info("注册用户[{}]，邮箱[{}]", user.getUserName(), user.getEmail());
            log.info("OAuth 对象情况：provider={}, oauthId={}, userName={}",
                    userOAuth.getProvider(), userOAuth.getOauthId(), userOAuth.getUserName());
            userOAuthService.saveUserUserOAuth(userOAuth,user);
            try {
                String ticket = userOAuthService.generateActivateTicket();
                // 保存 ticket 到 Redis
                userOAuthService.saveTicketByProviderOAuthId(registrationId, oauthId, ticket);
                authenticationService.iniPermissionForNewUser(user);
                String url = String.format("%s/oauth/activate?ticket=%s", frontendBaseUrl, ticket);
                response.sendRedirect(url);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }else{
            // 从数据库获取用户实体
            User user = userService.findUserByUsername(userOAuth.getUserName());

            try {
                authenticationService.checkUserStatus(user.getUserName());
            } catch (Exception e) {
                log.error("用户[{}]状态异常：{}", user.getUserName(), e.getMessage());

                if("disabled".equals(user.getState())){
                    response.sendRedirect(String.format("%s/oauth/error?message=%s", frontendBaseUrl, "该用户已经被禁止登录！"));
                }else if("pending".equals(user.getState())){
                    try {
                        String  ticket = userOAuthService.generateActivateTicket();
                        userOAuthService.saveTicketByProviderOAuthId(registrationId, oauthId, ticket);
                        String url = String.format("%s/oauth/activate?ticket=%s", frontendBaseUrl, ticket);
                        response.sendRedirect(url);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }

                }
                return;
            }

            // 加载权限
            final UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUserName());
            // 生成 accessToken 和 refreshToken（使用 uuid 作为 subject）
            String accessToken = jwtUtil.generateAccessTokenByUuid(user.getId(), userDetails.getAuthorities());
            String refreshToken = jwtUtil.generateRefreshTokenByUuid(user.getId(), userDetails.getAuthorities());

            // 把双 token 存入 redis（使用 uuid 作为 key）
            tokenStorageService.storeAccessToken(accessToken, user.getId());
            tokenStorageService.storeRefreshToken(refreshToken, user.getId());

            log.info("[{}]用户登录成功！UUID: {}", username, user.getId());

            String redirectUrl = String.format(
                    "%s/oauth/success?accessToken=%s&refreshToken=%s&username=%s&uuid=%s",
                    frontendBaseUrl, accessToken, refreshToken, user.getUserName(), user.getId()
            );

            response.sendRedirect(redirectUrl);
        }


    }


}
