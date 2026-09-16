package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AssetFingerprintRuleEntity {

    private Long id;
    private String ruleCode;
    private String name;
    private String category;
    private String subCategory;
    private String protocol;
    private Integer port;
    private String bannerRegex;
    private String vendor;
    private String product;
    private String versionExpr;
    private Integer confidence;
    private String description;
    private Integer enabled;
    private Integer priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
