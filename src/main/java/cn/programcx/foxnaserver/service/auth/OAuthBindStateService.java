package cn.programcx.foxnaserver.service.auth;

import cn.programcx.foxnaserver.dto.auth.OAuthBindState;

/**
 * OAuth 绑定状态服务接口
 * 用于管理 OAuth 绑定过程中的临时状态存储
 */
public interface OAuthBindStateService {

    /**
     * 生成随机的 state 值
     * @return UUID 字符串
     */
    String generateState();

    /**
     * 存储 OAuth 绑定状态到 Redis
     * @param state 状态对象
     */
    void saveBindState(OAuthBindState state);

    /**
     * 根据 state 获取绑定状态
     * @param state 状态码
     * @return 绑定状态对象，如果不存在或已过期则返回 null
     */
    OAuthBindState getBindState(String state);

    /**
     * 根据 state 获取并删除绑定状态（一次性使用）
     * @param state 状态码
     * @return 绑定状态对象，如果不存在或已过期则返回 null
     */
    OAuthBindState getAndRemoveBindState(String state);

    /**
     * 删除绑定状态
     * @param state 状态码
     */
    void removeBindState(String state);

    /**
     * 检查 state 是否存在且有效
     * @param state 状态码
     * @return true 如果存在且未过期
     */
    boolean isValidState(String state);

    /**
     * 创建并存储新的绑定状态
     * @param todo 待办事项标识（如 BIND_username）
     * @param redirectUrl 回调地址
     * @param provider OAuth 提供商
     * @return 生成的 state 值
     */
    String createBindState(String todo, String redirectUrl, String provider);
}
