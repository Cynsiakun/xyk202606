package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 全局自动探测策略：系统仅维护一行（id=1）。
 *
 * <p>调度器据此决定是否启用自动探测、探测周期（小时）以及探测内容（账号/服务/进程/应用）。
 * 各探测内容字段为 1/0，{@code lastRunAt} 记录上次实际下发时间用于按周期判定。</p>
 */
@Data
public class ProbeStrategyEntity {

    private Long id;
    private Integer enabled;
    private Integer periodHours;
    private Integer probeAccount;
    private Integer probeService;
    private Integer probeProcess;
    private Integer probeApp;
    private LocalDateTime lastRunAt;
    private LocalDateTime updatedAt;
}
