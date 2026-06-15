package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BaselineRemediationRecordDTO {

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
