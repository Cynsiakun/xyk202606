package com.cd.common.config;

import com.cd.common.ws.AlertHandshakeInterceptor;
import com.cd.common.ws.AlertWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 注册全局告警 WebSocket 端点 {@code /ws/alerts}（原生 WebSocket，无 STOMP）。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final AlertWebSocketHandler alertWebSocketHandler;
    private final AlertHandshakeInterceptor alertHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(alertWebSocketHandler, "/ws/alerts")
                .addInterceptors(alertHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
