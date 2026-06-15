package com.cd.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 基线任务结果概览：用于「查看结果」弹窗顶部概览卡片。
 */
@Data
public class BaselineTaskResultOverviewDTO {

    private Long taskId;
    private Integer totalHostCount;
    private Integer finishedHostCount;
    /** 平均合规率，可能为 null（尚无结果）。 */
    private BigDecimal avgComplianceRate;
    /** 问题主机数（fail_count > 0）。 */
    private Integer failHostCount;
    private Integer problemRuleCount;
    private Integer passRuleCount;
    private Integer failRuleCount;
    private Integer errorRuleCount;
}
