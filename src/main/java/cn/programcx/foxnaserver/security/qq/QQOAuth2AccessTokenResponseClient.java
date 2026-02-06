package cn.programcx.foxnaserver.security.qq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * QQ OAuth2 Access Token Response Client
 * 用于处理 QQ OAuth2 的 form-urlencoded 格式响应
 */
@Slf4j
@Component
public class QQOAuth2AccessTokenResponseClient implements OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public OAuth2AccessTokenResponse getTokenResponse(OAuth2AuthorizationCodeGrantRequest authorizationGrantRequest) {
        ClientRegistration clientRegistration = authorizationGrantRequest.getClientRegistration();

        // 只处理 QQ OAuth
        if (!"qq".equals(clientRegistration.getRegistrationId())) {
            return null;
        }

        // 构建请求参数
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add(OAuth2ParameterNames.GRANT_TYPE, authorizationGrantRequest.getGrantType().getValue());
        parameters.add(OAuth2ParameterNames.CODE, authorizationGrantRequest.getAuthorizationExchange()
                .getAuthorizationResponse().getCode());
        parameters.add(OAuth2ParameterNames.REDIRECT_URI, authorizationGrantRequest.getAuthorizationExchange()
                .getAuthorizationRequest().getRedirectUri());
        parameters.add(OAuth2ParameterNames.CLIENT_ID, clientRegistration.getClientId());
        parameters.add(OAuth2ParameterNames.CLIENT_SECRET, clientRegistration.getClientSecret());

        // 构建请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(Collections.singletonList(MediaType.ALL));

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(parameters, headers);

        try {
            // 发送请求获取原始响应
            ResponseEntity<String> response = restTemplate.exchange(
                    clientRegistration.getProviderDetails().getTokenUri(),
                    HttpMethod.POST,
                    request,
                    String.class
            );

            String responseBody = response.getBody();
            log.debug("QQ OAuth2 token response: {}", responseBody);

            // 解析 JSONP 格式的响应
            Map<String, Object> tokenAttributes = parseQQTokenResponse(responseBody);

            // 构建 OAuth2AccessTokenResponse
            return OAuth2AccessTokenResponse.withToken(tokenAttributes.get("access_token").toString())
                    .tokenType(OAuth2AccessToken.TokenType.BEARER)
                    .expiresIn(getExpiresIn(tokenAttributes))
                    .refreshToken(getRefreshToken(tokenAttributes))
                    .additionalParameters(tokenAttributes)
                    .build();

        } catch (Exception e) {
            log.error("Failed to get QQ OAuth2 access token", e);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_token_response", e.getMessage(), null),
                    e
            );
        }
    }

    /**
     * 解析 QQ OAuth2 的 form-urlencoded 格式响应
     * QQ 返回格式: access_token=xxx&expires_in=3600&refresh_token=xxx
     */
    private Map<String, Object> parseQQTokenResponse(String response) {
        Map<String, Object> result = new HashMap<>();

        if (response == null || response.isEmpty()) {
            throw new RuntimeException("Empty response from QQ OAuth2");
        }

        // 解析 form-urlencoded 格式 (key=value&key=value)
        String[] pairs = response.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                String key = kv[0].trim();
                String value = kv[1].trim();
                result.put(key, value);
            }
        }

        if (!result.containsKey("access_token")) {
            throw new RuntimeException("No access_token in QQ OAuth2 response: " + response);
        }

        return result;
    }

    private long getExpiresIn(Map<String, Object> attributes) {
        Object expiresIn = attributes.get("expires_in");
        if (expiresIn != null) {
            try {
                return Long.parseLong(expiresIn.toString());
            } catch (NumberFormatException e) {
                log.warn("Invalid expires_in value: {}", expiresIn);
            }
        }
        return 3600; // 默认 1 小时
    }

    private String getRefreshToken(Map<String, Object> attributes) {
        Object refreshToken = attributes.get("refresh_token");
        return refreshToken != null ? refreshToken.toString() : null;
    }
}
