package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VulnRuleEntity {

    private Long id;
    private String productType;
    private String productName;
    private String matchType;
    private String affectedVersionExpr;
    private String severity;
    private String title;
    private String suggestion;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
