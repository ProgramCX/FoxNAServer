package cn.programcx.foxnaserver.controller.auth;

import cn.programcx.foxnaserver.service.auth.AuthenticationService;
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



}

@Data
class LoginRequest {
    @Schema(description = "用户名", example = "admin")
    private String username;
    @Schema(description = "密码", example = "123456")
    private String password;
}

