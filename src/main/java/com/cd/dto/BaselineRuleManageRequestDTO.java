package com.cd.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BaselineRuleManageRequestDTO {

    @NotBlank(message = "规则编码不能为空")
    private String ruleCode;

    @NotBlank(message = "规则名称不能为空")
    private String ruleName;

    @NotBlank(message = "分类不能为空")
    private String category;

    private String description;

    @NotBlank(message = "风险等级不能为空")
    private String severity;

    private Integer score;

    @NotBlank(message = "适用系统不能为空")
    private String osType;

    @NotBlank(message = "检测方式不能为空")
    private String checkMethod;

    @NotBlank(message = "检测脚本不能为空")
    private String checkScript;

    @NotBlank(message = "修复方式不能为空")
    private String remediationType;

    private String remediationScript;

    private Integer isMandatory;

    private Integer enabled;

    private String status;
}
