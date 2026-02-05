package cn.programcx.foxnaserver.service.auth;

import jakarta.servlet.http.HttpServletRequest;

public interface AppOAuthService {

    String generateAuthUrl(HttpServletRequest request);
    String getAccessToken(String code);
    String generateRedirectUrl(HttpServletRequest request);
}
