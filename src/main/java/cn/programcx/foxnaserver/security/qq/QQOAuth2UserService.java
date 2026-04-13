package cn.programcx.foxnaserver.security.qq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

/**
 * QQ OAuth2 User Service
 * 用于处理 QQ OAuth2 的用户信息获取
 * QQ 需要先用 access_token 获取 openid，再用 openid 获取用户信息
 */
@Slf4j
@Component
public class QQOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        // 只处理 QQ OAuth
        if (!"qq".equals(registrationId)) {
            return null;
        }

        String accessToken = userRequest.getAccessToken().getTokenValue();

        try {
            // 第一步：获取 OpenID
            String openid = getOpenId(accessToken);
            log.debug("QQ OpenID: {}", openid);

            // 第二步：获取用户信息
            Map<String, Object> userAttributes = getUserInfo(accessToken, openid, userRequest);
            log.debug("QQ User Info: {}", userAttributes);

            // 添加 openid 到用户属性
            userAttributes.put("openid", openid);

            return new DefaultOAuth2User(
                    Collections.singleton(new OAuth2UserAuthority(userAttributes)),
                    userAttributes,
                    "openid"  // nameAttributeKey
            );

        } catch (Exception e) {
            log.error("Failed to load QQ user info", e);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("user_info_error", e.getMessage(), null),
                    e
            );
        }
    }

    /**
     * 获取 QQ OpenID
     * 接口: https://graph.qq.com/oauth2.0/me?access_token=xxx
     */
    private String getOpenId(String accessToken) {
        String url = UriComponentsBuilder
                .fromHttpUrl("https://graph.qq.com/oauth2.0/me")
                .queryParam("access_token", accessToken)
                .toUriString();

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        String responseBody = response.getBody();
        log.debug("QQ OpenID response: {}", responseBody);

        // 解析 JSONP 响应: callback({"client_id":"xxx","openid":"xxx"})
        return parseOpenIdResponse(responseBody);
    }

    /**
     * 获取 QQ 用户信息
     * 接口: https://graph.qq.com/user/get_user_info?access_token=xxx&oauth_consumer_key=xxx&openid=xxx
     */
    private Map<String, Object> getUserInfo(String accessToken, String openid, OAuth2UserRequest userRequest) {
        String appId = userRequest.getClientRegistration().getClientId();

        String url = UriComponentsBuilder
                .fromHttpUrl("https://graph.qq.com/user/get_user_info")
                .queryParam("access_token", accessToken)
                .queryParam("oauth_consumer_key", appId)
                .queryParam("openid", openid)
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        Map<String, Object> userInfo = response.getBody();

        if (userInfo == null) {
            throw new RuntimeException("Empty user info response from QQ");
        }

        // 检查返回码
        Object ret = userInfo.get("ret");
        if (ret == null || !ret.toString().equals("0")) {
            String msg = userInfo.get("msg") != null ? userInfo.get("msg").toString() : "Unknown error";
            throw new RuntimeException("QQ API error: " + msg);
        }

        return userInfo;
    }

    /**
     * 解析 QQ OpenID JSONP 响应
     */
    private String parseOpenIdResponse(String response) {
        if (response == null || response.isEmpty()) {
            throw new RuntimeException("Empty openid response from QQ");
        }

        // 移除 JSONP 包装: callback({"client_id":"xxx","openid":"xxx"})
        String json;
        if (response.contains("callback(") && response.endsWith(")")) {
            int start = response.indexOf("callback(") + 9;
            int end = response.lastIndexOf(")");
            json = response.substring(start, end);
        } else if (response.startsWith("(") && response.endsWith(")")) {
            json = response.substring(1, response.length() - 1);
        } else {
            json = response;
        }

        // 解析 JSON 获取 openid
        try {
            // 简单的字符串解析
            int openidIndex = json.indexOf("\"openid\"");
            if (openidIndex == -1) {
                openidIndex = json.indexOf("'openid'");
            }
            if (openidIndex == -1) {
                throw new RuntimeException("No openid found in response: " + response);
            }

            int colonIndex = json.indexOf(":", openidIndex);
            int quoteStart = json.indexOf("\"", colonIndex);
            if (quoteStart == -1) {
                quoteStart = json.indexOf("'", colonIndex);
            }
            int quoteEnd = json.indexOf("\"", quoteStart + 1);
            if (quoteEnd == -1) {
                quoteEnd = json.indexOf("'", quoteStart + 1);
            }

            if (quoteStart == -1 || quoteEnd == -1) {
                throw new RuntimeException("Failed to parse openid from response: " + response);
            }

            return json.substring(quoteStart + 1, quoteEnd);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse openid response: " + response, e);
        }
    }
}
