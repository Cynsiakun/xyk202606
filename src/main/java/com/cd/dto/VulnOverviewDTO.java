package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VulnOverviewDTO {

    private Long ruleId;
    private String ruleCode;
    private String cveId;
    private String severity;
    private String title;
    private String description;
    private String productType;
    private String productName;
    private Long totalHostCount;
    private Long pendingCount;
    private Long verifyingCount;
    private Long repairCount;
    private Long fixedCount;
    private LocalDateTime latestUpdatedAt;
}
