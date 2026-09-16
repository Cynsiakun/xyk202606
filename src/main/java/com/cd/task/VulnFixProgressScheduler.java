package com.cd.task;

import com.cd.mapper.HostVulnResultMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VulnFixProgressScheduler {

    private static final int FIX_DURATION_SECONDS = 15;

    private final HostVulnResultMapper hostVulnResultMapper;

    @Scheduled(fixedDelay = 3_000, initialDelay = 10_000)
    public void progressRepairing() {
        try {
            int updated = hostVulnResultMapper.completeRepairingByTenant(null, FIX_DURATION_SECONDS);
            if (updated > 0) {
                log.info("漏洞修复进度推进: {} 条结果从 REPAIRING → FIXED", updated);
            }
        } catch (Exception e) {
            log.error("漏洞修复进度推进失败", e);
        }
    }
}
