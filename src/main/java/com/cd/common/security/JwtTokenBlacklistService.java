package com.cd.common.security;

import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JwtTokenBlacklistService {

    private final Map<String, Instant> revokedTokens = new ConcurrentHashMap<>();

    public void revokeToken(String token, Claims claims) {
        clearExpired();
        revokedTokens.put(token, claims.getExpiration().toInstant());
    }

    public boolean isRevoked(String token) {
        clearExpired();
        Instant expireAt = revokedTokens.get(token);
        return expireAt != null && expireAt.isAfter(Instant.now());
    }

    private void clearExpired() {
        Instant now = Instant.now();
        revokedTokens.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
