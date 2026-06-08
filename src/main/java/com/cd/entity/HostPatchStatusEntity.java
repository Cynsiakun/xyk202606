package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HostPatchStatusEntity {

    private Long id;
    private Long hostId;
    private String osFamily;
    private String osBuild;
    private String kernelVersion;
    private String supportStatus;
    private LocalDateTime lastBootTime;
    private Integer pendingReboot;
    private String assetCriticality;
    private String riskLevel;
    private Integer missingPatchCount;
    private LocalDateTime scanTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
