package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PatchRiskDetailDTO {

    private Long id;
    private Long hostId;
    private String riskId;
    private String riskType;
    private String riskName;
    private String riskLevel;
    private String relatedPatchId;
    private String relatedCve;
    private String evidence;
    private String recommendation;
    private String status;
    private LocalDateTime scanTime;
}
