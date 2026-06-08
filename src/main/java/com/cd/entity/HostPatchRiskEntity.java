package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HostPatchRiskEntity {

    private Long id;
    private Long hostId;
    private String riskId;
    private String riskType;
    private String riskName;
    private String riskLevel;
    private String relatedPatchId;
    private String status;
    private LocalDateTime scanTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String relatedCve;
    private String evidence;
    private String recommendation;
}
