package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VulnHostOverviewDTO {

    private Long hostId;
    private String hostname;
    private String ipv4;
    private String macAddress;
    private String osName;
    private String osVersion;
    private Integer hostStatus;
    private String highestSeverity;
    private Long totalVulnCount;
    private Long pendingCount;
    private Long pendingRuleCount;
    private Long verifyingCount;
    private Long verifiedCount;
    private Long repairCount;
    private Long fixedCount;
    private Long ignoredCount;
    private LocalDateTime latestScanTime;
}
