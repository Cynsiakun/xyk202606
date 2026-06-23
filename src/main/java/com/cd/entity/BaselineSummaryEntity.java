package com.cd.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 基线统计汇总：按 (host_id, task_id) 唯一组合记录某台主机在某次任务下的通过/失败数量、
 * 得分与合规率，供主机视角与首页统计使用。
 */
@Data
public class BaselineSummaryEntity {

    private Long id;
    private Long tenantId;
    private Long hostId;
    private Long taskId;
    private Integer level;
    private Integer passCount;
    private Integer failCount;
    private Integer score;
    private BigDecimal complianceRate;
    private LocalDateTime lastScanTime;
    private LocalDateTime updateTime;
    private Long assetTypeId;
    private Long protectionLevelId;
}
