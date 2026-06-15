package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自动修复记录。修复成功后保留备份数据，供后续回滚下发使用。
 */
@Data
public class BaselineRemediationEntity {

    private Long id;
    private Long resultId;
    private Long hostId;
    private Long ruleId;
    private String remediationType;
    private String oldValue;
    private String newValue;
    private String backupData;
    private String executeScript;
    private String operator;
    private String message;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
}
