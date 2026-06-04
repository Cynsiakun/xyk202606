package com.cd.task;

import com.cd.service.HostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 主机离线检测定时任务。
 *
 * <p>每 10 秒批量将「最后活跃时间早于（当前时间 - 15 秒）且仍为在线」的主机置为离线。
 * 心跳每 3 秒一次，15 秒阈值允许连续丢失 3~4 个心跳并预留网络与处理延迟，避免瞬间抖动误报。</p>
 *
 * <p>采用批量定时更新而非「查询时逐个判断」，更节省数据库资源，离线状态收敛及时，
 * 且不依赖前端查询。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HostStatusScheduler {

    /** 离线判定阈值（秒）。 */
    private static final int OFFLINE_THRESHOLD_SECONDS = 15;

    private final HostService hostService;

    @Scheduled(fixedRate = 10_000)
    public void detectOfflineHosts() {
        try {
            int offlineCount = hostService.markOfflineHosts(OFFLINE_THRESHOLD_SECONDS);
            if (offlineCount > 0) {
                log.info("离线检测：已将 {} 台主机标记为离线", offlineCount);
            }
        } catch (Exception e) {
            log.error("离线检测任务执行失败", e);
        }
    }
}
