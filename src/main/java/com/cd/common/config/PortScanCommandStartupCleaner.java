package com.cd.common.config;

import com.cd.entity.ProbeStrategyEntity;
import com.cd.service.ProbeStrategyService;
import com.cd.service.impl.AgentCommandCleanupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortScanCommandStartupCleaner implements ApplicationRunner {

    private final ProbeStrategyService probeStrategyService;
    private final AgentCommandCleanupService agentCommandCleanupService;

    @Override
    public void run(ApplicationArguments args) {
        ProbeStrategyEntity strategy = probeStrategyService.getStrategyEntity();
        boolean shouldClean = strategy == null
                || strategy.getEnabled() == null
                || strategy.getEnabled() != 1
                || strategy.getProbePortScan() == null
                || strategy.getProbePortScan() != 1;
        if (!shouldClean) {
            return;
        }
        int removedCount = agentCommandCleanupService.clearPendingPortScanCommandsForAllHosts();
        log.info("启动时端口扫描残留指令清理完成: removedCount={}", removedCount);
    }
}
