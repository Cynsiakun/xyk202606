package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HostVulnTaskEntity {

    private Long id;
    private Long tenantId;
    private String taskName;
    private String taskType;
    private Long hostId;
    private String macAddress;
    private String scanMode;
    private Integer ruleCount;
    private Integer status;
    private String triggeredBy;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String summaryJson;
    private LocalDateTime createdAt;
}
