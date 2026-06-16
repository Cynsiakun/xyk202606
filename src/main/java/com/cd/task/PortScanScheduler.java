package com.cd.task;

import com.cd.entity.ProbeStrategyEntity;
import com.cd.service.HostService;
import com.cd.service.ProbeStrategyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortScanScheduler {

    private static final int AUTO_SCAN_LIMIT = 20;
    private static final long CHECK_INTERVAL_MS = 60_000L;

    private final HostService hostService;
    private final ProbeStrategyService probeStrategyService;

    @Scheduled(fixedDelay = CHECK_INTERVAL_MS, initialDelay = CHECK_INTERVAL_MS)
    public void autoPortScanOnlineHosts() {
        try {
            ProbeStrategyEntity strategy = probeStrategyService.getStrategyEntity();
            if (strategy == null || strategy.getEnabled() == null || strategy.getEnabled() != 1) {
                return;
            }

            boolean portScan = strategy.getProbePortScan() != null && strategy.getProbePortScan() == 1;
            if (!portScan) {
                return;
            }

            int periodHours = strategy.getPeriodHours() == null ? 8 : strategy.getPeriodHours();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime lastRun = strategy.getLastPortScanAt();
            if (lastRun != null && Duration.between(lastRun, now).toMinutes() < periodHours * 60L) {
                return;
            }

            probeStrategyService.markPortScanRun(now);

            int sentCount = hostService.autoPortScanOnlineHosts(AUTO_SCAN_LIMIT);
            if (sentCount > 0) {
                log.info("自动端口扫描任务已下发: count={}, periodHours={}", sentCount, periodHours);
            }
        } catch (Exception e) {
            log.error("自动端口扫描任务执行失败", e);
        }
    }
}
