package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VulnRuleResponseDTO {

    private Long id;
    private String ruleCode;
    private String cveId;
    private String category;
    private String productType;
    private String productName;
    private String matchType;
    private String affectedVersionExpr;
    private String severity;
    private String title;
    private String description;
    private String suggestion;
    private String verifyType;
    private String verifyRule;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
