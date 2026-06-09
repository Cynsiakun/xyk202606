package com.cd.dto;

import lombok.Data;

@Data
public class VulnDetectionSummaryDTO {

    private Long criticalCount;
    private Long highCount;
    private Long mediumCount;
    private Long lowCount;
    private Long pendingCount;
    private Long verifyingCount;
    private Long verifiedCount;
    private Long repairCount;
    private Long fixedCount;
    private Long ignoredCount;
}
