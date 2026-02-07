package cn.programcx.foxnaserver.api.auth.oauth;

import cn.programcx.foxnaserver.dto.auth.ActivateResponse;
import cn.programcx.foxnaserver.dto.auth.ActivateUserOAuth;
import cn.programcx.foxnaserver.entity.User;
import cn.programcx.foxnaserver.entity.UserOAuth;
import cn.programcx.foxnaserver.mapper.UserMapper;
import cn.programcx.foxnaserver.service.auth.OAuthBindStateService;
import cn.programcx.foxnaserver.service.auth.UserOAuthService;
import cn.programcx.foxnaserver.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/api/auth/oauth")
@ResponseBody
@Tag(name = "OAuth", description = "OAuth 认证相关接口")
public class OAuthController {

    @Autowired
    private UserOAuthService userOAuthService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private OAuthBindStateService oAuthBindStateService;

    /**
     * 发送 OAuth 激活验证码
     * @param request 包含邮箱地址的请求体
     * @return 发送结果
     */
    @Operation(
            summary = "发送 OAuth 激活验证码",
            description = "向指定邮箱发送 OAuth 账户激活所需的验证码"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "验证码发送成功",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "验证码已发送")
                    )),
            @ApiResponse(responseCode = "400", description = "发送失败",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "邮箱格式错误")
                    ))
    })
    @PostMapping("/sendActivateCode")
    public ResponseEntity<?> sendActivateCode(@RequestBody SendCodeRequest request) {
        try {
            userOAuthService.sendActivationEmailCode(request.getEmail());
            return ResponseEntity.ok().body("验证码已发送");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @Operation(
            summary = "激活 OAuth 账户",
            description = "使用邮箱验证码和密码激活 OAuth 账户"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "激活成功",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "激活成功")
                    )),
            @ApiResponse(responseCode = "400", description = "激活失败",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "验证码错误")
                    ))
    })
    @PostMapping("/activate")
    public ResponseEntity<?> activate(@RequestBody ActivateRequest request) {
        // 校验验证码
        try{
            userOAuthService.verifyActivationEmailCode(request.getEmail(),request.getCode());
            // 校验 ticket
            ActivateUserOAuth activateUserOAuth = userOAuthService.getOAuthUserInfoByTicket(request.getTicket());
            if(activateUserOAuth == null){
                return ResponseEntity.badRequest().body("ticket 无效");
            }
            // 删除 ticket
            userOAuthService.deleteTicketByTicket(request.getTicket());
            ActivateResponse response = userOAuthService.activateUser(request.getEmail(),
                    request.getPassword(),
                    activateUserOAuth.getProvider(),
                    activateUserOAuth.getOAuthId());

            if(response.getUsername() == null){
                return ResponseEntity.badRequest().body("激活失败");
            }

            return ResponseEntity.ok().body(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/bindOAuth")
    public ResponseEntity<?> bindOAuth(@RequestBody BindOAuthRequest request,
                                        HttpServletRequest servletRequest,
                                        HttpServletResponse httpResponse) {
        try {
            String uuid = JwtUtil.getCurrentUuid();
            boolean isValid = userOAuthService.validatePassword(
                    uuid,
                    request.getPassword()
            );
            log.info("Password validation result for user [{}]: {}", uuid, isValid);
            if(!isValid){
                return ResponseEntity.badRequest().body("用户名或密码错误");
            }

            LambdaQueryWrapper<User> userQueryWrapper = new LambdaQueryWrapper<>();
            userQueryWrapper.eq(User::getId,uuid);
            User user = userMapper.selectOne(userQueryWrapper);
            if(user == null){
                return ResponseEntity.badRequest().body("用户不存在");
            }
             // 校验 OAuth 账户是否已绑定
            LambdaQueryWrapper<UserOAuth> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(UserOAuth::getProvider,request.getProvider())
                    .eq(UserOAuth::getUserName,user.getUserName());
            if(userOAuthService.count(queryWrapper)>0){
                return ResponseEntity.badRequest().body("已经绑定过该第三方账号");
            }

            // 使用 Redis 存储绑定状态
            String todo = "BIND_" + user.getUserName();
            String state = oAuthBindStateService.createBindState(todo, request.getRedirectUrl(), request.getProvider());
            log.info("Created OAuth bind state in Redis: state={}, user={}, provider={}",
                    state, user.getUserName(), request.getProvider());

            // 将 state 设置到 Cookie 中
            // 根据请求是否安全（HTTPS）动态设置 SameSite
            boolean isSecure = servletRequest.isSecure();
            String sameSite = isSecure ? "None" : "Lax";
            String secureFlag = isSecure ? "; Secure" : "";
            String cookieValue = String.format("OAUTH_BIND_STATE=%s; Path=/; HttpOnly; Max-Age=600; SameSite=%s%s",
                    state, sameSite, secureFlag);
            httpResponse.addHeader("Set-Cookie", cookieValue);
            log.info("Set OAuth bind state cookie: state={}, sameSite={}, secure={}", state, sameSite, isSecure);

            // 返回授权 URL
            String url = String.format("/api/oauth2/authorization/%s", request.getProvider());

            return ResponseEntity.ok().body(url);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


    @GetMapping("/getBindedOAuthInfo")
    public ResponseEntity<?> getBindedOAuthInfo(){
        String uuid = JwtUtil.getCurrentUuid();
        List<OAuthInfo> oAuthInfos = new ArrayList<>();
        LambdaQueryWrapper<User> userQueryWrapper = new LambdaQueryWrapper<>();
        userQueryWrapper.eq(User::getId,uuid);
        User user = userMapper.selectOne(userQueryWrapper);

        LambdaQueryWrapper<UserOAuth> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserOAuth::getUserName,user.getUserName());
        List<UserOAuth> userOAuths = userOAuthService.list(queryWrapper);
        for(UserOAuth userOAuth : userOAuths){
            OAuthInfo oAuthInfo = new OAuthInfo();
            oAuthInfo.setProvider(userOAuth.getProvider());
            oAuthInfo.setOauthId(userOAuth.getOauthId());
            oAuthInfos.add(oAuthInfo);
        }
        return ResponseEntity.ok(oAuthInfos);
    }

    @PostMapping("/unBindOAuth")
    public ResponseEntity<?> unBindOAuth(
                                         @RequestBody UnBindOAuthRequest request) {
        String uuid = JwtUtil.getCurrentUuid();
        boolean isValid = userOAuthService.validatePassword(
                uuid,
                request.getPassword()
        );
        if(!isValid){
            return ResponseEntity.badRequest().body("用户名或密码错误");
        }
        LambdaQueryWrapper<User> userQueryWrapper = new LambdaQueryWrapper<>();
        userQueryWrapper.eq(User::getId,uuid);
        User user = userMapper.selectOne(userQueryWrapper);
        if(user == null){
            return ResponseEntity.badRequest().body("用户不存在");
        }
        String username = user.getUserName();
       LambdaQueryWrapper<UserOAuth> queryWrapper = new LambdaQueryWrapper<>();
         queryWrapper.eq(UserOAuth::getUserName,username)
                .eq(UserOAuth::getProvider,request.getProvider());
        UserOAuth userOAuth = userOAuthService.getOne(queryWrapper);
        if(userOAuth == null){
            return ResponseEntity.badRequest().body("OAuth 绑定信息不存在");
        }
        userOAuthService.removeById(userOAuth.getId());
        return ResponseEntity.ok().body("解绑成功");
    }


    @Data
    static class SendCodeRequest {
        private String email;
    }

    @Data
    static class ActivateRequest {
        private String password;
        private String email;
        private String code;
        private String ticket;
    }

    @Data
    static class BindOAuthRequest {
        private String provider;
        private String password;
        private String redirectUrl;
    }

    @Data
    static class UnBindOAuthRequest {
        private String provider;
        private String password;
        private String redirectUrl;
    }

    @Data
    static class OAuthInfo {
        private String provider;
        private String oauthId;
    }

}
