package cn.programcx.foxnaserver.controller.auth;

import cn.programcx.foxnaserver.exception.VerificationCodeColdTimeException;
import cn.programcx.foxnaserver.service.auth.AuthenticationService;
import cn.programcx.foxnaserver.service.auth.VerificationService;
import cn.programcx.foxnaserver.service.log.ErrorLogService;
import cn.programcx.foxnaserver.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@RestController
@RequestMapping(("/api/auth"))
@Tag(name = "Authentication", description = "用户认证相关接口")
public class AuthenticationController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ErrorLogService errorLogService;

    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private VerificationService verificationService;

    @Operation(
            summary = "用户登录",
            description = "使用用户名和密码进行用户登录，成功后返回JWT令牌token"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "登录成功，返回JWT令牌",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
                    )),
            @ApiResponse(responseCode = "401", description = "认证失败"
            , content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Invalid username or password")
                    )),
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {

        try {
            authenticationService.checkUserStatus(loginRequest.getUsername());
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            //生成token
            String token = jwtUtil.generateToken(authentication);

            log.info("[{}]用户登录成功！", loginRequest.getUsername());

            return ResponseEntity.ok(token);
        } catch (UsernameNotFoundException e) {
            log.info("[{}]用户登录失败，找不到用户名！", loginRequest.getUsername());
            errorLogService.insertErrorLog(request, e, "找不到用户名: " + loginRequest.getUsername());
            return ResponseEntity.status(401).body("Invalid username or password");
        } catch (BadCredentialsException e) {
            log.info("[{}]用户登录失败，凭据无效！", loginRequest.getUsername());
            errorLogService.insertErrorLog(request, e, "无效的凭据: " + loginRequest.getUsername());
            return ResponseEntity.status(401).body("Invalid username or password");
        } catch (Exception e) {
            log.info("[{}]用户登录失败，发生异常：{}", loginRequest.getUsername(), e.getMessage());
            errorLogService.insertErrorLog(request, e, "登录错误: " + loginRequest.getUsername());
            return ResponseEntity.status(401).body("Login failed" + e.getMessage());
        }


    }

    @Operation(
            summary = "初始化管理员用户",
            description = "注册管理员用户，若已存在则返回冲突状态"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "管理员用户注册成功",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "123456")
                    )),
            @ApiResponse(responseCode = "409", description = "管理员用户已存在",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "admin already registered")
                    ))
    })
    @PostMapping("/iniAdmin")
    public ResponseEntity<String> iniAdmin(HttpServletRequest request) {
        if (authenticationService.registerAdmin()) {
            log.info("管理员用户注册成功！");
            return ResponseEntity.ok("123456");
        } else {
            // errorLogService.insertErrorLog(request, new Exception("Admin already exists"), "管理员用户注册失败，因为管理员已存在！");
            return ResponseEntity.status(HttpStatus.CONFLICT).body("admin already registered");
        }
    }

    @PostMapping("/sendVerifyCode")
    @Operation(
            summary = "发送验证码",
            description = "向指定邮箱发送验证码，用于用户注册或密码重置"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "验证码发送成功",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Verification code sent successfully")
                    )),
            @ApiResponse(responseCode = "500", description = "验证码发送失败",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Failed to send verification code")
                    ))
    })
        public ResponseEntity<String> sendVerifyCode(@RequestBody EmailRequest em, HttpServletRequest request) {
                if (em == null || em.getEmailAddr() == null || em.getEmailAddr().isBlank()) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email address is required");
                }

                try {
                        verificationService.sendVerificationCode(em.getEmailAddr());
                        return ResponseEntity.ok("Verification code sent successfully");
                } catch (VerificationCodeColdTimeException e) {
                        log.warn("发送验证码被限流: {}", e.getMessage());
                        errorLogService.insertErrorLog(request, e, "发送验证码被限流: " + e.getMessage());
                        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(e.getMessage().trim());
                } catch (Exception e) {
                        log.error("发送验证码失败: {}", e.getMessage());
                        errorLogService.insertErrorLog(request, e, "发送验证码失败到邮箱: " + e.getMessage());
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .body("Failed to send verification code: " + e.getMessage());
                }
        }

    @PostMapping("reg")
    @Operation(
            summary = "用户注册",
            description = "使用用户名、密码和验证码进行用户注册"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "用户注册成功",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "User registered successfully")
                    )),
            @ApiResponse(responseCode = "400", description = "用户注册失败",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Registration failed: User already exists")
                    ))
    })
    public ResponseEntity<String> registerUser(@RequestBody RegisterRequest reg, HttpServletRequest request) {
        try {
            authenticationService.registerUser(reg.getUsername(), reg.getPassword(), reg.getCode());
            log.info("用户[{}]注册成功！", reg.getUsername());
            return ResponseEntity.ok("User registered successfully");
        } catch (Exception e) {
            log.info("用户[{}]注册失败：{}", reg.getUsername(), e.getMessage());
            errorLogService.insertErrorLog(request, e, "用户注册失败: " + reg.getUsername() + "，原因：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Registration failed: " + e.getMessage());
        }
    }


}

@Data
class LoginRequest {
    @Schema(description = "用户名", example = "admin")
    private String username;
    @Schema(description = "密码", example = "123456")
    private String password;
}

@Data
class EmailRequest {
    @Schema(description = "邮箱地址", example = "programcx@qq.com")
    private String emailAddr;
}

@Data
class RegisterRequest {
    @Schema(description = "用户名", example = "user1")
    private String username;
    @Schema(description = "密码", example = "password123")
    private String password;
    @Schema(description = "验证码", example = "123456")
    private String code;
}

