package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BaselineRuleEntity {

    private Long id;
    private String ruleCode;
    private String ruleName;
    private String category;
    private String description;
    private String severity;
    private Integer score;
    private Integer level;
    private String assetType;
    private String assetTypeCode;
    private String osType;
    private String checkMethod;
    private String checkScript;
    private String remediationType;
    private String remediationScript;
    private Integer version;
    private Integer isMandatory;
    private Integer enabled;
    private String status;
    private String protectionLevelFlag;
    private String groupCombineLogic;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
