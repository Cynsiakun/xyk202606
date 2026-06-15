package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BaselineWorkorderDetailDTO {

    private Long id;
    private Long resultId;
    private Long hostId;
    private String hostName;
    private String ipv4;
    private String macAddress;
    private Long ruleId;
    private String ruleName;
    private String ruleCode;
    private String category;
    private String title;
    private String advice;
    private Long assigneeId;
    private String assigneeName;
    private String priority;
    private String status;
    private String closeRemark;
    private Long createBy;
    private String creatorName;
    private LocalDateTime createTime;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
    private String checkKey;
    private String expectedValue;
    private String actualValue;
    private String evidence;
    private String resultStatus;
}
