package cn.programcx.foxnaserver.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {
        log.error("OAuth2登录失败: {}", exception.getMessage(), exception);
        
        // 获取并记录所有请求参数，帮助调试
        StringBuilder params = new StringBuilder();
        request.getParameterMap().forEach((k, v) -> {
            params.append(k).append("=").append(String.join(",", v)).append("&");
        });
        log.error("OAuth2失败请求参数: {}", params.toString());
        
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "OAuth2登录失败: " + exception.getMessage());
    }
}
