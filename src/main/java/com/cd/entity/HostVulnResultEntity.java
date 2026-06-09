package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HostVulnResultEntity {

    private Long id;
    private Long hostId;
    private Long ruleId;
    private String severity;
    private String vulnName;
    private String productName;
    private String productVersion;
    private String suggestion;
    private Integer status;
    private String verifyStatus;
    private String evidenceJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
