package com.cd.dto;

import lombok.Data;

@Data
public class VulnVerificationRuleDTO {

    private Long resultId;
    private Long hostId;
    private Long ruleId;
    private String productType;
    private String productName;
    private String matchType;
    private String versionExpression;
    private String verifyType;
    private String verifyRule;
}
