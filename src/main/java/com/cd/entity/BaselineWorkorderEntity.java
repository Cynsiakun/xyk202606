package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 基线整改工单：对手动/半自动修复的不合规规则派单，记录负责人与备注。
 */
@Data
public class BaselineWorkorderEntity {

    private Long id;
    private Long hostId;
    private Long ruleId;
    private Long resultId;
    private String assignee;
    private String remark;
    private String status;
    private String creator;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
