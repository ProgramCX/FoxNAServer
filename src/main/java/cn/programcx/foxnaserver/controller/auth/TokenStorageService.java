package cn.programcx.foxnaserver.controller.auth;

import cn.programcx.foxnaserver.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenStorageService {
    private final String ACCESS_TOKEN_PREFIX = "FoxNAS:AccessToken:";
    private final String REFRESH_TOKEN_PREFIX = "FoxNAS:RefreshToken:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private JwtUtil jwtUtil;

    // 存储刷新访问令牌
    public void storeRefreshToken(String token, String username) {
        final String key = REFRESH_TOKEN_PREFIX + username;
        stringRedisTemplate.opsForValue()
                .set(key, token, jwtUtil.REFRESH_TOKEN_EXPIRATION);
    }

    public String getRefreshToken(String username) {
        final String key = REFRESH_TOKEN_PREFIX + username;
        return stringRedisTemplate.opsForValue().get(key);
    }

    public void deleteRefreshToken(String username) {
        final String key = REFRESH_TOKEN_PREFIX + username;
        stringRedisTemplate.delete(key);
    }

    public void storeAccessToken(String token, String username) {
        final String key = ACCESS_TOKEN_PREFIX + username;
        stringRedisTemplate.opsForValue()
                .set(key, token, jwtUtil.ACCESS_TOKEN_EXPIRATION);
    }

    public String getAccessToken(String username) {
        final String key = ACCESS_TOKEN_PREFIX + username;
        return stringRedisTemplate.opsForValue().get(key);
    }

    public void deleteAccessToken(String username) {
        final String key = ACCESS_TOKEN_PREFIX + username;
        stringRedisTemplate.delete(key);
    }

    // 根据 token 值删除 AccessToken（用于登出）
    public void deleteAccessTokenByToken(String token) {
        String username = jwtUtil.getUsername(token);
        if (username != null) {
            deleteAccessToken(username);
        }
    }
}
