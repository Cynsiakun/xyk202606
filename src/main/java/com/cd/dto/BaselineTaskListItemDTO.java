package com.cd.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 基线任务列表行：用于「基线任务管理」主表格展示。
 */
@Data
public class BaselineTaskListItemDTO {

    private Long id;
    private String taskName;
    private String executeType;
    private String taskType;
    private Integer ruleCount;
    private Integer hostCount;
    private Integer finishedHostCount;
    /** 平均通过率（该任务下所有主机合规率均值），可能为 null（尚无结果）。 */
    private BigDecimal avgPassRate;
    private String status;
    private LocalDateTime createTime;
}
