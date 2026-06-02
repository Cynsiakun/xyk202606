package com.cd.common.auth;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginSessionManager {

    // 当前使用内存 token 存储，后续可平滑替换为 JWT 或集中式会话方案。
    private final Map<String, Long> tokenStore = new ConcurrentHashMap<>();

    public String createToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenStore.put(token, userId);
        return token;
    }

    public Long getUserId(String token) {
        return tokenStore.get(token);
    }

    public void removeToken(String token) {
        tokenStore.remove(token);
    }
}
