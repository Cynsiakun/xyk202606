package com.cd.dto;

import lombok.Data;

/**
 * 规则选项：用于新建任务弹窗中的规则多选列表（仅启用且已发布的规则）。
 */
@Data
public class BaselineRuleOptionDTO {

    private Long id;
    private String ruleCode;
    private String ruleName;
    private String category;
    private String severity;
}
