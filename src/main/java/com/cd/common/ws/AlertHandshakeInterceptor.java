package com.cd.common.ws;

import com.cd.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * WebSocket 握手鉴权：浏览器 WebSocket 无法自定义请求头，故从查询参数 {@code token} 取 JWT，
 * 校验有效性与 {@code security-alert:view} 权限（超管放行），不通过则拒绝握手。
 */
@Component
@RequiredArgsConstructor
public class AlertHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_TENANT_ID = "tenantId";
    static final String ATTR_PLATFORM_ADMIN = "platformAdmin";
    private static final String VIEW_PERMISSION = "security-alert:view";
    private static final String SUPER_ADMIN = "ROLE_SUPER_ADMIN";
    private static final String WILDCARD = "*";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request.getURI());
        if (token == null || !jwtTokenProvider.isValid(token)) {
            return false;
        }
        List<String> authorities = jwtTokenProvider.getAuthorities(token);
        boolean allowed = authorities.contains(VIEW_PERMISSION)
                || authorities.contains(SUPER_ADMIN)
                || authorities.contains(WILDCARD);
        if (!allowed) {
            return false;
        }
        Long tenantId = jwtTokenProvider.getTenantId(token);
        Long currentTenantId = tenantId == null ? 0L : tenantId;
        attributes.put("userId", jwtTokenProvider.getUserId(token));
        attributes.put("userName", jwtTokenProvider.getUserName(token));
        attributes.put(ATTR_TENANT_ID, currentTenantId);
        attributes.put(ATTR_PLATFORM_ADMIN, isPlatformAdmin(currentTenantId, authorities));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private String extractToken(URI uri) {
        String query = uri.getQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx > 0 && "token".equals(pair.substring(0, idx))) {
                return pair.substring(idx + 1);
            }
        }
        return null;
    }

    private boolean isPlatformAdmin(Long tenantId, List<String> authorities) {
        return Long.valueOf(0L).equals(tenantId)
                && (authorities.contains(SUPER_ADMIN) || authorities.contains(WILDCARD));
    }
}
