package com.cd.common.ws;

import com.cd.dto.PopupAlertDTO;
import com.cd.service.SecurityEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 全局告警 WebSocket 处理器。维护所有在线会话，连接建立时下发当前未处理的
 * Critical/High 告警做初始同步，之后由 {@code AlertBroadcastTask} 推送新增告警。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertWebSocketHandler extends TextWebSocketHandler {

    private final SecurityEventService securityEventService;
    private final ObjectMapper objectMapper;

    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        try {
            List<PopupAlertDTO> recent = securityEventService.recentHighCritical(sessionTenantId(session), canViewAll(session));
            String payload = objectMapper.writeValueAsString(Map.of("type", "init", "data", recent));
            session.sendMessage(new TextMessage(payload));
        } catch (Exception e) {
            log.warn("发送初始告警同步失败: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session);
    }

    /** 向所有在线会话推送一条新告警。 */
    public void broadcast(PopupAlertDTO alert) {
        if (sessions.isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of("type", "alert", "data", alert));
        } catch (Exception e) {
            log.warn("序列化告警失败: {}", e.getMessage());
            return;
        }
        TextMessage message = new TextMessage(payload);
        Long alertTenantId = alert.getTenantId() == null ? 0L : alert.getTenantId();
        for (WebSocketSession session : sessions) {
            if (canViewAll(session) || alertTenantId.equals(sessionTenantId(session))) {
                sendQuietly(session, message);
            }
        }
    }

    private Long sessionTenantId(WebSocketSession session) {
        Object tenantId = session.getAttributes().get(AlertHandshakeInterceptor.ATTR_TENANT_ID);
        if (tenantId instanceof Long value) {
            return value;
        }
        if (tenantId instanceof Number value) {
            return value.longValue();
        }
        return 0L;
    }

    private boolean canViewAll(WebSocketSession session) {
        return Boolean.TRUE.equals(session.getAttributes().get(AlertHandshakeInterceptor.ATTR_PLATFORM_ADMIN));
    }

    private void sendQuietly(WebSocketSession session, TextMessage message) {
        try {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(message);
                }
            }
        } catch (IOException e) {
            sessions.remove(session);
        }
    }
}
