package cn.programcx.foxnaserver.service.auth.impl;

import cn.programcx.foxnaserver.dto.auth.OAuthBindState;
import cn.programcx.foxnaserver.service.auth.OAuthBindStateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * OAuth 绑定状态服务实现类
 * 使用 Redis 存储绑定状态，支持分布式部署
 */
@Slf4j
@Service
public class OAuthBindStateServiceImpl implements OAuthBindStateService {

    private static final String REDIS_KEY_PREFIX = "oauth:bind:state:";
    private static final long DEFAULT_EXPIRE_SECONDS = 600; // 默认 10 分钟过期

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String generateState() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public void saveBindState(OAuthBindState state) {
        if (state == null || state.getState() == null) {
            throw new IllegalArgumentException("State and state code cannot be null");
        }

        try {
            String key = REDIS_KEY_PREFIX + state.getState();
            String value = objectMapper.writeValueAsString(state);
            long expireSeconds = state.getExpireSeconds() > 0 ? state.getExpireSeconds() : DEFAULT_EXPIRE_SECONDS;

            stringRedisTemplate.opsForValue().set(key, value, expireSeconds, TimeUnit.SECONDS);
            log.info("Saved OAuth bind state to Redis: state={}, provider={}, expire={}s",
                    state.getState(), state.getProvider(), expireSeconds);
        } catch (Exception e) {
            log.error("Failed to save OAuth bind state to Redis: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save bind state", e);
        }
    }

    @Override
    public OAuthBindState getBindState(String state) {
        if (state == null || state.isEmpty()) {
            return null;
        }

        try {
            String key = REDIS_KEY_PREFIX + state;
            String value = stringRedisTemplate.opsForValue().get(key);

            if (value == null) {
                log.warn("OAuth bind state not found in Redis: state={}", state);
                return null;
            }

            OAuthBindState bindState = objectMapper.readValue(value, OAuthBindState.class);

            // 检查是否已过期（虽然 Redis 会自动过期，但双重检查更安全）
            if (bindState.isExpired()) {
                log.warn("OAuth bind state expired: state={}", state);
                removeBindState(state);
                return null;
            }

            return bindState;
        } catch (Exception e) {
            log.error("Failed to get OAuth bind state from Redis: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public OAuthBindState getAndRemoveBindState(String state) {
        OAuthBindState bindState = getBindState(state);
        if (bindState != null) {
            removeBindState(state);
            log.info("Retrieved and removed OAuth bind state: state={}", state);
        }
        return bindState;
    }

    @Override
    public void removeBindState(String state) {
        if (state == null || state.isEmpty()) {
            return;
        }

        try {
            String key = REDIS_KEY_PREFIX + state;
            stringRedisTemplate.delete(key);
            log.debug("Removed OAuth bind state from Redis: state={}", state);
        } catch (Exception e) {
            log.error("Failed to remove OAuth bind state from Redis: {}", e.getMessage(), e);
        }
    }

    @Override
    public boolean isValidState(String state) {
        return getBindState(state) != null;
    }

    @Override
    public String createBindState(String todo, String redirectUrl, String provider) {
        String stateCode = generateState();
        OAuthBindState state = new OAuthBindState(stateCode, todo, redirectUrl, provider);
        saveBindState(state);
        return stateCode;
    }
}
