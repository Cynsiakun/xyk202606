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
public class AssetProbeScheduler {

    private static final int AUTO_PROBE_LIMIT = 20;
    private static final long CHECK_INTERVAL_MS = 60_000L;

    private final HostService hostService;
    private final ProbeStrategyService probeStrategyService;

    @Scheduled(fixedDelay = CHECK_INTERVAL_MS, initialDelay = CHECK_INTERVAL_MS)
    public void autoProbeOnlineHosts() {
        try {
            ProbeStrategyEntity strategy = probeStrategyService.getStrategyEntity();
            if (strategy == null || strategy.getEnabled() == null || strategy.getEnabled() != 1) {
                return;
            }

            boolean account = strategy.getProbeAccount() != null && strategy.getProbeAccount() == 1;
            boolean service = strategy.getProbeService() != null && strategy.getProbeService() == 1;
            boolean process = strategy.getProbeProcess() != null && strategy.getProbeProcess() == 1;
            boolean app = strategy.getProbeApp() != null && strategy.getProbeApp() == 1;
            boolean portScan = strategy.getProbePortScan() != null && strategy.getProbePortScan() == 1;
            boolean fingerprint = strategy.getProbeFingerprint() != null && strategy.getProbeFingerprint() == 1;
            if (!account && !service && !process && !app && !portScan) {
                return;
            }

            int periodHours = strategy.getPeriodHours() == null ? 8 : strategy.getPeriodHours();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime lastRun = strategy.getLastRunAt();
            if (lastRun != null && Duration.between(lastRun, now).toMinutes() < periodHours * 60L) {
                return;
            }

            probeStrategyService.markRun(now);

            int sentCount = hostService.autoProbeOnlineHosts(
                    AUTO_PROBE_LIMIT, account, service, process, app, portScan, fingerprint);
            if (sentCount > 0) {
                log.info("自动资产探测任务已下发: count={}, periodHours={}, portScan={}",
                        sentCount, periodHours, portScan);
            }
        } catch (Exception e) {
            log.error("自动资产探测任务执行失败", e);
        }
    }
}
