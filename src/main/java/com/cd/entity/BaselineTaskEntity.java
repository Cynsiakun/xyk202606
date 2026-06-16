package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BaselineTaskEntity {

    private Long id;
    private Long tenantId;
    private String taskName;
    private String executeType;
    private String cronExpr;
    private String ruleScope;
    private String ruleSnapshotJson;
    private String status;
    private Integer totalHostCount;
    private Integer successCount;
    private Integer failCount;
    private String creator;
    private LocalDateTime createTime;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
}
