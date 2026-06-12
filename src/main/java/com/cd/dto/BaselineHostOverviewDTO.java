package com.cd.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 主机合规总览卡片：取该主机最近一次基线汇总。
 */
@Data
public class BaselineHostOverviewDTO {

    private Long hostId;
    private String hostName;
    private String ipv4;
    private String osName;
    private BigDecimal complianceRate;
    private Integer totalCount;
    private Integer passCount;
    private Integer failCount;
    private LocalDateTime lastScanTime;
    private Long taskId;
}
