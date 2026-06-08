package com.cd.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RiskContext {

    private Long hostId;
    private String riskType;
    private String riskName;
    private String relatedPatchId;
    private String relatedCve;
    private String evidence;
    private String recommendation;

    private BigDecimal cvssScore;
    private Integer kevFlag;
    private String exploitStatus;
    private String issueSeverity;
    private Boolean pendingReboot;
    private String installStatus;
    private String supportStatus;
    private String assetCriticality;
}
