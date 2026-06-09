package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PatchRiskHostDTO {

    private Long hostId;
    private String hostname;
    private String ipv4;
    private String macAddress;
    private String osName;
    private String osVersion;
    private String osBuild;
    private Integer hostStatus;
    private String highestRiskLevel;
    private Integer riskCount;
    private String riskTypes;
    private Integer pendingReboot;
    private LocalDateTime lastPatchScanTime;
}
