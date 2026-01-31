package cn.programcx.foxnaserver.controller.auth;

import cn.programcx.foxnaserver.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class TokenStorageService {
    private final String ACCESS_TOKEN_PREFIX = "FoxNAS:AccessToken:";
    private final String REFRESH_TOKEN_PREFIX = "FoxNAS:RefreshToken:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private JwtUtil jwtUtil;

    // 存储刷新访问令牌（使用 uuid 作为 key）
    public void storeRefreshToken(String token, String uuid) {
        final String key = REFRESH_TOKEN_PREFIX + uuid;
        stringRedisTemplate.opsForValue()
                .set(key, token, jwtUtil.REFRESH_TOKEN_EXPIRATION, TimeUnit.MILLISECONDS);
    }

    public String getRefreshToken(String uuid) {
        final String key = REFRESH_TOKEN_PREFIX + uuid;
        return stringRedisTemplate.opsForValue().get(key);
    }

    public void deleteRefreshToken(String uuid) {
        final String key = REFRESH_TOKEN_PREFIX + uuid;
        stringRedisTemplate.delete(key);
    }

    public void storeAccessToken(String token, String uuid) {
        final String key = ACCESS_TOKEN_PREFIX + uuid;
        stringRedisTemplate.opsForValue()
                .set(key, token, jwtUtil.ACCESS_TOKEN_EXPIRATION, TimeUnit.MILLISECONDS);
    }

    public String getAccessToken(String uuid) {
        final String key = ACCESS_TOKEN_PREFIX + uuid;
        return stringRedisTemplate.opsForValue().get(key);
    }

    public void deleteAccessToken(String uuid) {
        final String key = ACCESS_TOKEN_PREFIX + uuid;
        stringRedisTemplate.delete(key);
    }

    // 根据 token 值删除 AccessToken（用于登出）
    public void deleteAccessTokenByToken(String token) {
        String uuid = jwtUtil.getUsername(token);
        if (uuid != null) {
            deleteAccessToken(uuid);
        }
    }

    public boolean isTokenRedisValid(String token, String uuid) {
        final String keyAccess = ACCESS_TOKEN_PREFIX + uuid;
        final String keyRefresh = REFRESH_TOKEN_PREFIX + uuid;
        String storedAccessToken = stringRedisTemplate.opsForValue().get(keyAccess);
        String storedRefreshToken = stringRedisTemplate.opsForValue().get(keyRefresh);
        return token.equals(storedAccessToken) || token.equals(storedRefreshToken);
    }
}
