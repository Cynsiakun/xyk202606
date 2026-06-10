package com.cd.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VulnRuleCreateDTO {

    @NotBlank(message = "ruleCode不能为空")
    private String ruleCode;

    private String cveId;
    private String category;

    @NotBlank(message = "severity不能为空")
    private String severity;

    @NotBlank(message = "title不能为空")
    private String title;

    private String description;
    private String suggestion;

    @NotBlank(message = "productType不能为空")
    private String productType;

    @NotBlank(message = "productName不能为空")
    private String productName;

    @NotBlank(message = "matchType不能为空")
    private String matchType;

    private String affectedVersionExpr;

    @NotBlank(message = "verifyType不能为空")
    private String verifyType;

    private String verifyRule;
    private Integer enabled;
}
