package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BaselineWorkorderListItemDTO {

    private Long id;
    private String title;
    private Long hostId;
    private String hostName;
    private String ipv4;
    private Long ruleId;
    private String ruleName;
    private Long assigneeId;
    private String assigneeName;
    private String priority;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
}
