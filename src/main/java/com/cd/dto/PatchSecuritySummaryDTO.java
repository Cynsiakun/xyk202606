package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PatchSecuritySummaryDTO {

    private Long riskyHostCount;
    private Long totalRiskCount;
    private Long criticalCount;
    private Long highCount;
    private Long mediumCount;
    private Long lowCount;
    private Long pendingRebootHostCount;
    private Long missingPatchHostCount;
    private Long badPatchHostCount;
    private Long installFailureHostCount;
    private Long eolHostCount;
    private Long unscannedHostCount;
    private LocalDateTime latestScanTime;
}
