package cn.programcx.foxnaserver.api.auth;

import cn.programcx.foxnaserver.entity.User;
import cn.programcx.foxnaserver.exception.VerificationCodeColdTimeException;
import cn.programcx.foxnaserver.mapper.UserMapper;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    @Autowired
    private TokenStorageService tokenStorageService;
    @Autowired
    private UserMapper userMapper;

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

            // 获取用户 UUID
            String username = loginRequest.getUsername();
            LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(User::getUserName, username);
            User user = userMapper.selectOne(queryWrapper);
            String userUuid = user.getId();

            // 生成 accessToken 和 refreshToken（使用 uuid 作为 subject）
            String accessToken = jwtUtil.generateAccessTokenByUuid(userUuid, authentication.getAuthorities());
            String refreshToken = jwtUtil.generateRefreshTokenByUuid(userUuid, authentication.getAuthorities());

            // 把双 token 存入 redis（使用 uuid 作为 key）
            tokenStorageService.storeAccessToken(accessToken, userUuid);
            tokenStorageService.storeRefreshToken(refreshToken, userUuid);

            log.info("[{}]用户登录成功！UUID: {}", username, userUuid);

            TokenResponse token = new TokenResponse();
            token.setAccessToken(accessToken);
            token.setRefreshToken(refreshToken);

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
            description = "向指定邮箱发送验证码，用于用户注册、密码重置或找回用户名"
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
            authenticationService.registerUser(reg.getUsername(),reg.getEmailAddr() ,reg.getPassword(), reg.getCode());
            log.info("用户[{}]注册成功！", reg.getUsername());
            return ResponseEntity.ok("User registered successfully");
        } catch (Exception e) {
            log.info("用户[{}]注册失败：{}", reg.getUsername(), e.getMessage());
            errorLogService.insertErrorLog(request, e, "用户注册失败: " + reg.getUsername() + "，原因：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Registration failed: " + e.getMessage());
        }
    }

    @PostMapping("/password/reset")
    @Operation(
            summary = "重置密码",
            description = "通过邮箱验证码重置密码"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "密码重置成功",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Password reset successfully"))),
            @ApiResponse(responseCode = "400", description = "密码重置失败",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Password reset failed: reason")))
    })
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequest resetRequest, HttpServletRequest request) {
        if (resetRequest == null || resetRequest.getEmailAddr() == null || resetRequest.getEmailAddr().isBlank()
                || resetRequest.getCode() == null || resetRequest.getCode().isBlank()
                || resetRequest.getNewPassword() == null || resetRequest.getNewPassword().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email, code and new password are required");
        }

        try {
            authenticationService.resetPasswordByEmail(resetRequest.getEmailAddr(), resetRequest.getCode(), resetRequest.getNewPassword());
            log.info("邮箱[{}]密码重置成功", resetRequest.getEmailAddr());
            return ResponseEntity.ok("Password reset successfully");
        } catch (Exception e) {
            log.info("邮箱[{}]密码重置失败：{}", resetRequest.getEmailAddr(), e.getMessage());
            errorLogService.insertErrorLog(request, e, "密码重置失败: " + resetRequest.getEmailAddr() + "，原因：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Password reset failed: " + e.getMessage());
        }
    }

    @PostMapping("/username/retrieve")
    @Operation(
            summary = "找回用户名",
            description = "通过邮箱验证码找回用户名，将用户名发送到邮箱"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "用户名已发送到邮箱",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Username sent to email"))),
            @ApiResponse(responseCode = "400", description = "找回失败",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Username recovery failed: reason")))
    })
    public ResponseEntity<String> retrieveUsername(@RequestBody ForgotUsernameRequest forgotRequest, HttpServletRequest request) {
        if (forgotRequest == null || forgotRequest.getEmailAddr() == null || forgotRequest.getEmailAddr().isBlank()
                || forgotRequest.getCode() == null || forgotRequest.getCode().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email and code are required");
        }

        try {
            authenticationService.sendUsernameByEmail(forgotRequest.getEmailAddr(), forgotRequest.getCode());
            log.info("邮箱[{}]找回用户名成功，用户名已发送", forgotRequest.getEmailAddr());
            return ResponseEntity.ok("Username sent to email");
        } catch (Exception e) {
            log.info("邮箱[{}]找回用户名失败：{}", forgotRequest.getEmailAddr(), e.getMessage());
            errorLogService.insertErrorLog(request, e, "找回用户名失败: " + forgotRequest.getEmailAddr() + "，原因：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Username recovery failed: " + e.getMessage());
        }
    }

    @PostMapping("/logout")
    @Operation(
            summary = "用户登出",
            description = "用户登出，删除存储的访问令牌和刷新令牌"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "用户登出成功",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Logged out")
                    ))
    })
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        String accessToken = authHeader.substring(7);
        String userUuid = JwtUtil.getCurrentUuid();

        // 删除双 Token（使用 uuid）
        tokenStorageService.deleteAccessTokenByToken(accessToken);
        tokenStorageService.deleteRefreshToken(userUuid);

        return ResponseEntity.ok("Logged out");
    }

    @GetMapping("/initRequired")
    @Operation(
            summary = "检查是否需要初始化管理员",
            description = "返回是否系统中没有管理员账户"
    )
    public ResponseEntity<?> initRequired() {
        long userCount = userMapper.selectCount(null);
        if (userCount == 0) {
            return ResponseEntity.ok(Map.of("required", true));
        }
        return ResponseEntity.ok(Map.of("required", false));
    }

    @PostMapping("/login/email")
    @Operation(
            summary = "邮箱登录（密码方式）",
            description = "使用邮箱和密码进行登录，成功后返回JWT令牌"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "登录成功，返回JWT令牌",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TokenResponse.class)
                    )),
            @ApiResponse(responseCode = "401", description = "认证失败",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Invalid email or password")
                    )),
    })
    public ResponseEntity<?> loginByEmailWithPassword(@RequestBody EmailLoginPasswordRequest loginRequest, HttpServletRequest request) {
        try {
            if (loginRequest == null || loginRequest.getEmail() == null || loginRequest.getEmail().isBlank()
                    || loginRequest.getPassword() == null || loginRequest.getPassword().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email and password are required");
            }

            // 通过邮箱查找用户
            LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(User::getEmail, loginRequest.getEmail());
            User user = userMapper.selectOne(queryWrapper);

            if (user == null) {
                log.info("[{}]邮箱登录失败，邮箱未绑定任何用户！", loginRequest.getEmail());
                return ResponseEntity.status(401).body("Invalid email or password");
            }

            authenticationService.checkUserStatus(user.getUserName());

            // 验证密码
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUserName(), loginRequest.getPassword())
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            String userUuid = user.getId();

            // 生成 accessToken 和 refreshToken
            String accessToken = jwtUtil.generateAccessTokenByUuid(userUuid, authentication.getAuthorities());
            String refreshToken = jwtUtil.generateRefreshTokenByUuid(userUuid, authentication.getAuthorities());

            // 把双token存入redis
            tokenStorageService.storeAccessToken(accessToken, userUuid);
            tokenStorageService.storeRefreshToken(refreshToken, userUuid);

            log.info("[{}]邮箱登录成功！UUID: {}", loginRequest.getEmail(), userUuid);

            TokenResponse token = new TokenResponse();
            token.setAccessToken(accessToken);
            token.setRefreshToken(refreshToken);

            return ResponseEntity.ok(token);
        } catch (BadCredentialsException e) {
            log.info("[{}]邮箱登录失败，密码错误！", loginRequest.getEmail());
            errorLogService.insertErrorLog(request, e, "邮箱登录密码错误: " + loginRequest.getEmail());
            return ResponseEntity.status(401).body("Invalid email or password");
        } catch (Exception e) {
            log.info("[{}]邮箱登录失败，发生异常：{}", loginRequest.getEmail(), e.getMessage());
            errorLogService.insertErrorLog(request, e, "邮箱登录错误: " + loginRequest.getEmail());
            return ResponseEntity.status(401).body("Login failed: " + e.getMessage());
        }
    }

    @PostMapping("/login/email/code")
    @Operation(
            summary = "邮箱登录（验证码方式）",
            description = "使用邮箱和验证码进行登录，成功后返回JWT令牌"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "登录成功，返回JWT令牌",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TokenResponse.class)
                    )),
            @ApiResponse(responseCode = "401", description = "认证失败",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string", example = "Invalid email or verification code")
                    )),
    })
    public ResponseEntity<?> loginByEmailWithCode(@RequestBody EmailLoginCodeRequest loginRequest, HttpServletRequest request) {
        try {
            if (loginRequest == null || loginRequest.getEmail() == null || loginRequest.getEmail().isBlank()
                    || loginRequest.getCode() == null || loginRequest.getCode().isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email and verification code are required");
            }

            // 验证验证码
            try {
                verificationService.verifyCode(loginRequest.getEmail(), loginRequest.getCode());
            } catch (Exception e) {
                log.info("[{}]邮箱登录失败，验证码验证失败：{}", loginRequest.getEmail(), e.getMessage());
                return ResponseEntity.status(401).body("Invalid verification code: " + e.getMessage());
            }

            // 通过邮箱查找用户
            LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(User::getEmail, loginRequest.getEmail());
            User user = userMapper.selectOne(queryWrapper);

            if (user == null) {
                log.info("[{}]邮箱登录失败，邮箱未绑定任何用户！", loginRequest.getEmail());
                return ResponseEntity.status(401).body("Email not bound to any user");
            }

            authenticationService.checkUserStatus(user.getUserName());

            // 获取用户权限
            List<GrantedAuthority> authorities = authenticationService.getUserAuthorities(user.getUserName());

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user.getUserName(), null, authorities)
            );

            String userUuid = user.getId();

            // 生成 accessToken 和 refreshToken
            String accessToken = jwtUtil.generateAccessTokenByUuid(userUuid, authorities);
            String refreshToken = jwtUtil.generateRefreshTokenByUuid(userUuid, authorities);

            // 把双token存入redis
            tokenStorageService.storeAccessToken(accessToken, userUuid);
            tokenStorageService.storeRefreshToken(refreshToken, userUuid);

            log.info("[{}]邮箱验证码登录成功！UUID: {}", loginRequest.getEmail(), userUuid);

            TokenResponse token = new TokenResponse();
            token.setAccessToken(accessToken);
            token.setRefreshToken(refreshToken);

            return ResponseEntity.ok(token);
        } catch (Exception e) {
            log.info("[{}]邮箱验证码登录失败，发生异常：{}", loginRequest.getEmail(), e.getMessage());
            errorLogService.insertErrorLog(request, e, "邮箱验证码登录错误: " + loginRequest.getEmail());
            return ResponseEntity.status(401).body("Login failed: " + e.getMessage());
        }
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "刷新访问令牌",
            description = "使用刷新令牌刷新访问令牌，返回新的访问令牌和刷新令牌"
    )
    public ResponseEntity<?> refreshToken(@RequestBody Map<String,String> request) {
        String refreshToken = request.get("refreshToken");
        String userUuid = request.get("uuid");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Refresh token is required");
        }

        String storedRefreshToken = tokenStorageService.getRefreshToken(userUuid);
        if (storedRefreshToken == null || storedRefreshToken.isBlank() || !storedRefreshToken.equals(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid refresh token");
        }

        // 验证 refreshToken 是否有效
        if (!jwtUtil.isTokenValid(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Refresh token expired");
        }

        try {
            // 获取角色列表
            List<String> roles = jwtUtil.getRoles(refreshToken);
            List<GrantedAuthority> authorities = roles.stream()
                    .map(role -> new GrantedAuthority() {
                        @Override
                        public String getAuthority() {
                            return role.toUpperCase();
                        }
                    })
                    .collect(Collectors.toList());

            // 生成新的 token
            String newAccessToken = jwtUtil.generateAccessTokenByUuid(userUuid, authorities);
            String newRefreshToken = jwtUtil.generateRefreshTokenByUuid(userUuid, authorities);

            // 更新 Redis 中的 token
            tokenStorageService.storeAccessToken(newAccessToken, userUuid);
            tokenStorageService.storeRefreshToken(newRefreshToken, userUuid);

            log.info("[{}]令牌刷新成功！", userUuid);

            TokenResponse tokenResponse = new TokenResponse();
            tokenResponse.setAccessToken(newAccessToken);
            tokenResponse.setRefreshToken(newRefreshToken);

            return ResponseEntity.ok(tokenResponse);
        } catch (Exception e) {
            log.info("[{}]令牌刷新失败：{}", userUuid, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token refresh failed: " + e.getMessage());
        }
    }

    // ==================== 邮箱绑定相关接口 ====================

    @GetMapping("/email")
    @Operation(
            summary = "获取当前用户绑定的邮箱",
            description = "返回当前登录用户绑定的邮箱地址（部分隐藏保护隐私）"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "获取成功",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = EmailInfoResponse.class))),
            @ApiResponse(responseCode = "401", description = "未登录或登录已过期")
    })
    public ResponseEntity<?> getBoundEmail() {
        String userUuid = JwtUtil.getCurrentUuid();
        if (userUuid == null || userUuid.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not logged in");
        }

        User user = userMapper.selectById(userUuid);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }

        EmailInfoResponse response = new EmailInfoResponse();
        String email = user.getEmail();
        if (email != null && !email.isBlank()) {
            response.setEmail(maskEmail(email));
            response.setBound(true);
        } else {
            response.setEmail(null);
            response.setBound(false);
        }

        return ResponseEntity.ok(response);
    }

    @PostMapping("/email/bind")
    @Operation(
            summary = "绑定邮箱",
            description = "为当前登录用户绑定邮箱，需要验证码和密码验证"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "绑定成功",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Email bound successfully"))),
            @ApiResponse(responseCode = "400", description = "绑定失败",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Email already bound to another user"))),
            @ApiResponse(responseCode = "401", description = "密码错误或验证码错误")
    })
    public ResponseEntity<?> bindEmail(@RequestBody BindEmailRequest request, HttpServletRequest httpRequest) {
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()
                || request.getCode() == null || request.getCode().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email, code and password are required");
        }

        String userUuid = JwtUtil.getCurrentUuid();
        if (userUuid == null || userUuid.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not logged in");
        }

        User user = userMapper.selectById(userUuid);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }

        // 验证密码
        if (!authenticationService.verifyPassword(user.getUserName(), request.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid password");
        }

        // 验证验证码
        try {
            verificationService.verifyCode(request.getEmail(), request.getCode());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid verification code: " + e.getMessage());
        }

        // 检查邮箱是否已被其他用户绑定
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getEmail, request.getEmail());
        User existingUser = userMapper.selectOne(queryWrapper);
        if (existingUser != null && !existingUser.getId().equals(userUuid)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email already bound to another user");
        }

        // 绑定邮箱
        user.setEmail(request.getEmail());
        userMapper.updateById(user);

        log.info("用户[{}]绑定邮箱[{}]成功", user.getUserName(), request.getEmail());
        return ResponseEntity.ok("Email bound successfully");
    }

    @PostMapping("/email/unbind")
    @Operation(
            summary = "解绑邮箱",
            description = "解除当前登录用户绑定的邮箱，需要验证码和密码验证"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "解绑成功",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "Email unbound successfully"))),
            @ApiResponse(responseCode = "400", description = "解绑失败",
                    content = @Content(mediaType = "text/plain", schema = @Schema(type = "string", example = "No email bound"))),
            @ApiResponse(responseCode = "401", description = "密码错误或验证码错误")
    })
    public ResponseEntity<?> unbindEmail(@RequestBody UnbindEmailRequest request, HttpServletRequest httpRequest) {
        if (request == null || request.getCode() == null || request.getCode().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Code and password are required");
        }

        String userUuid = JwtUtil.getCurrentUuid();
        if (userUuid == null || userUuid.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not logged in");
        }

        User user = userMapper.selectById(userUuid);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
        }

        // 检查是否已绑定邮箱
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("No email bound");
        }

        // 验证密码
        if (!authenticationService.verifyPassword(user.getUserName(), request.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid password");
        }

        // 验证验证码（发送到当前绑定的邮箱）
        try {
            verificationService.verifyCode(user.getEmail(), request.getCode());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid verification code: " + e.getMessage());
        }

        // 解绑邮箱
        String oldEmail = user.getEmail();
        user.setEmail(null);
        userMapper.updateById(user);

        log.info("用户[{}]解绑邮箱[{}]成功", user.getUserName(), oldEmail);
        return ResponseEntity.ok("Email unbound successfully");
    }

    /**
     * 隐藏邮箱中间部分保护隐私
     * 例如：example@qq.com -> ex***@qq.com
     */
    private String maskEmail(String email) {
        if (email == null || email.isBlank() || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@");
        String localPart = parts[0];
        String domain = parts[1];

        if (localPart.length() <= 2) {
            return localPart.charAt(0) + "***@" + domain;
        }

        return localPart.charAt(0) + "***" + localPart.charAt(localPart.length() - 1) + "@" + domain;
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
    @Schema(description = "邮箱地址", example = "programcx@qq.com")
    private String emailAddr;
    @Schema(description = "密码", example = "password123")
    private String password;
    @Schema(description = "验证码", example = "123456")
    private String code;

}

@Data
class TokenResponse {
    @Schema(description = "访问令牌", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;
    @Schema(description = "刷新令牌", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String refreshToken;
}

@Data
class ResetPasswordRequest {
    @Schema(description = "邮箱地址", example = "programcx@qq.com")
    private String emailAddr;
    @Schema(description = "验证码", example = "123456")
    private String code;
    @Schema(description = "新密码", example = "newPassword123")
    private String newPassword;
}

@Data
class ForgotUsernameRequest {
    @Schema(description = "邮箱地址", example = "programcx@qq.com")
    private String emailAddr;
    @Schema(description = "验证码", example = "123456")
    private String code;
}

@Data
class EmailInfoResponse {
    @Schema(description = "邮箱地址（部分隐藏）", example = "ex***@qq.com")
    private String email;
    @Schema(description = "是否已绑定邮箱", example = "true")
    private boolean bound;
}

@Data
class BindEmailRequest {
    @Schema(description = "要绑定的邮箱地址", example = "newemail@qq.com")
    private String email;
    @Schema(description = "验证码", example = "123456")
    private String code;
    @Schema(description = "当前用户密码", example = "password123")
    private String password;
}

@Data
class UnbindEmailRequest {
    @Schema(description = "验证码", example = "123456")
    private String code;
    @Schema(description = "当前用户密码", example = "password123")
    private String password;
}

@Data
class EmailLoginPasswordRequest {
    @Schema(description = "邮箱地址", example = "user@example.com")
    private String email;
    @Schema(description = "密码", example = "password123")
    private String password;
}

@Data
class EmailLoginCodeRequest {
    @Schema(description = "邮箱地址", example = "user@example.com")
    private String email;
    @Schema(description = "验证码", example = "123456")
    private String code;
}
