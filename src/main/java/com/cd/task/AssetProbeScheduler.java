package com.cd.task;

import com.cd.service.HostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AssetProbeScheduler {

    private static final int AUTO_PROBE_LIMIT = 20;
    private static final long EIGHT_HOURS = 8L * 60 * 60 * 1000;

    private final HostService hostService;

    @Scheduled(fixedDelay = EIGHT_HOURS, initialDelay = 60_000)
    public void autoProbeOnlineHosts() {
        try {
            int sentCount = hostService.autoProbeOnlineHosts(AUTO_PROBE_LIMIT);
            if (sentCount > 0) {
                log.info("自动资产探测任务已下发: count={}", sentCount);
            }
        } catch (Exception e) {
            log.error("自动资产探测任务执行失败", e);
        }
    }
}
