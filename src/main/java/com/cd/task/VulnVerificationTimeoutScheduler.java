package com.cd.task;

import com.cd.mapper.HostVulnResultMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VulnVerificationTimeoutScheduler {

    private static final int VERIFY_TIMEOUT_SECONDS = 180;
    private static final String STATUS_VERIFYING = "VERIFYING";
    private static final String STATUS_PENDING = "PENDING";

    private final HostVulnResultMapper hostVulnResultMapper;

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void rollbackTimeoutVerifying() {
        try {
            int updated = hostVulnResultMapper.resetVerifyingTimeoutByTenant(
                    null,
                    VERIFY_TIMEOUT_SECONDS,
                    STATUS_VERIFYING,
                    STATUS_PENDING
            );
            if (updated > 0) {
                log.warn("漏洞验证超时回退: {} 条结果从 VERIFYING -> PENDING", updated);
            }
        } catch (Exception e) {
            log.error("漏洞验证超时回退失败", e);
        }
    }
}
