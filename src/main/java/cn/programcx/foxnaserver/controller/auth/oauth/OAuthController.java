package cn.programcx.foxnaserver.controller.auth.oauth;

import cn.programcx.foxnaserver.dto.auth.ActivateResponse;
import cn.programcx.foxnaserver.dto.auth.ActivateUserOAuth;
import cn.programcx.foxnaserver.entity.User;
import cn.programcx.foxnaserver.service.auth.UserOAuthService;
import cn.programcx.foxnaserver.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/api/auth/oauth")
@ResponseBody
@Tag(name = "OAuth", description = "OAuth 认证相关接口")
public class OAuthController {

    @Autowired
    private UserOAuthService userOAuthService;

    @Autowired
    private JwtUtil jwtUtil;
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

}
