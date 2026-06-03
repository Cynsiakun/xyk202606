package com.cd.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(toBase64(jwtProperties.getSecret())));
    }

    public String createToken(SecurityUser securityUser, List<String> roles) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(jwtProperties.getExpireSeconds());

        return Jwts.builder()
                .subject(securityUser.getUsername())
                .claims(Map.of(
                        "userId", securityUser.getUserId(),
                        "userName", securityUser.getUsername(),
                        "roles", roles,
                        "authorities", securityUser.getAuthorities().stream().map(Object::toString).toList()
                ))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        Object roles = parseClaims(token).get("roles");
        return roles instanceof List<?> list ? (List<String>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    public List<String> getAuthorities(String token) {
        Object authorities = parseClaims(token).get("authorities");
        return authorities instanceof List<?> list ? (List<String>) list : List.of();
    }

    public Long getUserId(String token) {
        Object userId = parseClaims(token).get("userId");
        if (userId instanceof Integer intValue) {
            return intValue.longValue();
        }
        if (userId instanceof Long longValue) {
            return longValue;
        }
        return Long.valueOf(String.valueOf(userId));
    }

    public String getUserName(String token) {
        return String.valueOf(parseClaims(token).get("userName"));
    }

    private String toBase64(String raw) {
        return java.util.Base64.getEncoder().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
