package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 主机详情中的单条规则检测结果，含修复类型与修复状态供前端决定操作按钮。
 */
@Data
public class BaselineHostResultItemDTO {

    private Long resultId;
    private Long ruleId;
    private String ruleName;
    private String category;
    private String checkKey;
    private String expectedValue;
    private String actualValue;
    private String evidence;
    private String status;
    private String remediationType;
    private String remediationStatus;
    private String latestRemediationStatus;
    private String latestRemediationType;
    private String latestRemediationOperator;
    private LocalDateTime latestRemediationStartTime;
    private LocalDateTime latestRemediationEndTime;
    private LocalDateTime scanTime;
}
