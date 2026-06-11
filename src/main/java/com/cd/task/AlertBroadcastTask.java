package com.cd.task;

import com.cd.common.ws.AlertWebSocketHandler;
import com.cd.dto.PopupAlertDTO;
import com.cd.service.SecurityEventService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 服务端定时探测新出现的 Critical/High 告警并通过 WebSocket 推送到所有在线前端。
 *
 * <p>不侵入现有告警写入链路：以告警自增 id 作为高水位，启动时取当前最大 id，
 * 之后只推送 id 更大的新告警，避免重复推送历史告警。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertBroadcastTask {

    private final SecurityEventService securityEventService;
    private final AlertWebSocketHandler alertWebSocketHandler;

    private final AtomicLong highWaterMark = new AtomicLong(0);

    @PostConstruct
    public void init() {
        highWaterMark.set(securityEventService.currentMaxId());
    }

    @Scheduled(fixedDelay = 8000, initialDelay = 8000)
    public void broadcastNewAlerts() {
        try {
            long mark = highWaterMark.get();
            List<PopupAlertDTO> fresh = securityEventService.newHighCritical(mark);
            if (fresh.isEmpty()) {
                return;
            }
            long maxId = mark;
            for (PopupAlertDTO alert : fresh) {
                alertWebSocketHandler.broadcast(alert);
                if (alert.getId() != null && alert.getId() > maxId) {
                    maxId = alert.getId();
                }
            }
            highWaterMark.set(maxId);
        } catch (Exception e) {
            log.warn("告警推送任务执行失败: {}", e.getMessage());
        }
    }
}
