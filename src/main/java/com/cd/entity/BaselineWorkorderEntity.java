package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 基线整改工单：对手动/半自动修复的不合规规则派单，记录负责人与备注。
 */
@Data
public class BaselineWorkorderEntity {

    private Long id;
    private Long resultId;
    private Long hostId;
    private Long ruleId;
    private String title;
    private String advice;
    private Long assigneeId;
    private String priority;
    private String status;
    private String closeRemark;
    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
    private LocalDateTime updateTime;
}
