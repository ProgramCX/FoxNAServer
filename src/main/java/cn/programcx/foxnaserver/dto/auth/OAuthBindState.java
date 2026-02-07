package cn.programcx.foxnaserver.dto.auth;

import lombok.Data;

import java.io.Serializable;

/**
 * OAuth 绑定状态数据类
 * 用于存储 OAuth 绑定过程中的临时状态信息
 */
@Data
public class OAuthBindState implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 随机状态码（UUID）
     */
    private String state;

    /**
     * 待办事项标识，如 BIND_username
     */
    private String todo;

    /**
     * 绑定完成后的回调地址
     */
    private String redirectUrl;

    /**
     * OAuth 提供商（github、qq 等）
     */
    private String provider;

    /**
     * 创建时间戳
     */
    private long createdAt;

    /**
     * 过期时间（秒）
     */
    private long expireSeconds;

    public OAuthBindState() {
        this.createdAt = System.currentTimeMillis();
    }

    public OAuthBindState(String state, String todo, String redirectUrl, String provider) {
        this();
        this.state = state;
        this.todo = todo;
        this.redirectUrl = redirectUrl;
        this.provider = provider;
        this.expireSeconds = 600; // 默认 10 分钟过期
    }

    /**
     * 检查是否已过期
     */
    public boolean isExpired() {
        long now = System.currentTimeMillis();
        return (now - createdAt) > (expireSeconds * 1000);
    }
}
