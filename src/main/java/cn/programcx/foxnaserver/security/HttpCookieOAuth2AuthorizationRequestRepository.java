package cn.programcx.foxnaserver.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 基于 Cookie 的 OAuth2 授权请求存储库
 * 用于在无状态（STATELESS）session 策略下存储 OAuth2 授权请求
 * 
 * 注意：Spring Security 6.x 中 OAuth2AuthorizationRequest 不再实现 Serializable，
 * 因此使用 JSON 序列化代替 Java 原生序列化
 */
@Slf4j
@Component
public class HttpCookieOAuth2AuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    public static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_auth_request";
    private static final int COOKIE_EXPIRE_SECONDS = 180; // 3分钟过期
    
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        log.debug("Loading authorization request from cookie");
        Cookie cookie = getCookie(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        if (cookie == null) {
            log.debug("No oauth2_auth_request cookie found");
            return null;
        }
        
        OAuth2AuthorizationRequest authRequest = deserialize(cookie.getValue());
        if (authRequest != null) {
            log.debug("Successfully loaded authorization request, state: {}", authRequest.getState());
        }
        return authRequest;
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            log.debug("Removing authorization request cookie");
            deleteCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
            return;
        }

        log.debug("Saving authorization request, state: {}", authorizationRequest.getState());
        String serialized = serialize(authorizationRequest);
        if (serialized == null) {
            log.error("Failed to serialize authorization request");
            return;
        }

        // 检查 Cookie 大小，如果超过 4KB 需要警告
        if (serialized.length() > 4096) {
            log.warn("OAuth2 authorization request cookie size ({} bytes) exceeds 4KB limit, may cause issues", serialized.length());
        }

        // 根据请求是否安全（HTTPS）动态设置 SameSite 和 Secure
        boolean isSecure = request.isSecure();
        String sameSite = isSecure ? "None" : "Lax";
        
        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie
                .from(OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, serialized)
                .path("/")
                .httpOnly(true)
                .maxAge(COOKIE_EXPIRE_SECONDS)
                .sameSite(sameSite);
        
        if (isSecure) {
            cookieBuilder.secure(true);
        }

        response.addHeader(HttpHeaders.SET_COOKIE, cookieBuilder.build().toString());
        log.debug("Authorization request cookie saved successfully, sameSite={}, secure={}", sameSite, isSecure);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        log.debug("Removing authorization request");
        OAuth2AuthorizationRequest authorizationRequest = loadAuthorizationRequest(request);
        if (authorizationRequest != null) {
            deleteCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
            log.debug("Authorization request removed, state: {}", authorizationRequest.getState());
        } else {
            log.warn("No authorization request found to remove");
        }
        return authorizationRequest;
    }

    /**
     * 清除 OAuth2 相关的 Cookie
     */
    public void removeAuthorizationRequestCookies(HttpServletRequest request, HttpServletResponse response) {
        deleteCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
    }

    /**
     * 从请求中获取指定名称的 Cookie
     */
    private Cookie getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    return cookie;
                }
            }
        }
        return null;
    }

    /**
     * 删除指定名称的 Cookie
     */
    private void deleteCookie(HttpServletRequest request, HttpServletResponse response, String name) {
        // 使用 ResponseCookie 删除 Cookie，保持 SameSite 一致性
        boolean isSecure = request.isSecure();
        String sameSite = isSecure ? "None" : "Lax";
        
        ResponseCookie.ResponseCookieBuilder cookieBuilder = ResponseCookie
                .from(name, "")
                .path("/")
                .maxAge(0)
                .sameSite(sameSite);
        
        if (isSecure) {
            cookieBuilder.secure(true);
        }
        
        response.addHeader(HttpHeaders.SET_COOKIE, cookieBuilder.build().toString());
        log.debug("Cookie {} deleted, sameSite={}, secure={}", name, sameSite, isSecure);
    }

    /**
     * 序列化 OAuth2AuthorizationRequest 为 Base64 编码的 JSON 字符串
     */
    private String serialize(OAuth2AuthorizationRequest authRequest) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("authorizationUri", authRequest.getAuthorizationUri());
            data.put("clientId", authRequest.getClientId());
            data.put("redirectUri", authRequest.getRedirectUri());
            data.put("scopes", authRequest.getScopes());
            data.put("state", authRequest.getState());
            data.put("authorizationRequestUri", authRequest.getAuthorizationRequestUri());
            data.put("attributes", authRequest.getAttributes());
            data.put("additionalParameters", authRequest.getAdditionalParameters());
            
            // 保存 grant type
            if (authRequest.getGrantType() != null) {
                data.put("grantType", authRequest.getGrantType().getValue());
            }
            
            String json = objectMapper.writeValueAsString(data);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize OAuth2AuthorizationRequest", e);
            return null;
        }
    }

    /**
     * 反序列化 Base64 编码的 JSON 字符串为 OAuth2AuthorizationRequest
     */
    @SuppressWarnings("unchecked")
    private OAuth2AuthorizationRequest deserialize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        
        try {
            String json = new String(Base64.getUrlDecoder().decode(value));
            Map<String, Object> data = objectMapper.readValue(json, Map.class);
            
            OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri((String) data.get("authorizationUri"))
                    .clientId((String) data.get("clientId"))
                    .redirectUri((String) data.get("redirectUri"))
                    .state((String) data.get("state"))
                    .authorizationRequestUri((String) data.get("authorizationRequestUri"));
            
            if (data.get("scopes") != null) {
                builder.scopes(new java.util.HashSet<>((java.util.Collection<String>) data.get("scopes")));
            }
            
            if (data.get("attributes") != null) {
                builder.attributes(attrs -> attrs.putAll((Map<String, Object>) data.get("attributes")));
            }
            
            if (data.get("additionalParameters") != null) {
                builder.additionalParameters((Map<String, Object>) data.get("additionalParameters"));
            }
            
            return builder.build();
        } catch (Exception e) {
            log.error("Failed to deserialize OAuth2AuthorizationRequest", e);
            return null;
        }
    }
}
