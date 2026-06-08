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

/**
 * 自动资产探测调度器。
 *
 * <p>每分钟检查一次全局探测策略（{@code probe_strategy} 表），是否真正下发取决于：
 * 策略是否启用、距上次执行是否已满配置周期、以及是否勾选了任一探测内容。
 * 由于每次执行都重新读取数据库配置，修改策略后无需重启系统即可生效。</p>
 */
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
            if (!account && !service && !process && !app) {
                return;
            }

            int periodHours = strategy.getPeriodHours() == null ? 8 : strategy.getPeriodHours();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime lastRun = strategy.getLastRunAt();
            if (lastRun != null && Duration.between(lastRun, now).toMinutes() < periodHours * 60L) {
                return;
            }

            // 标记本周期已触发，避免在线主机为空时每分钟重复下发。
            probeStrategyService.markRun(now);

            int sentCount = hostService.autoProbeOnlineHosts(AUTO_PROBE_LIMIT, account, service, process, app);
            if (sentCount > 0) {
                log.info("自动资产探测任务已下发: count={}, periodHours={}", sentCount, periodHours);
            }
        } catch (Exception e) {
            log.error("自动资产探测任务执行失败", e);
        }
    }
}
