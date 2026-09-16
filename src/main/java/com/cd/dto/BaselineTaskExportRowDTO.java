package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BaselineTaskExportRowDTO {

    private Long hostId;
    private String hostName;
    private String ipv4;
    private String osName;
    private Long ruleId;
    private String ruleName;
    private String category;
    private String severity;
    private String assetType;
    private String protectionLevel;
    private String checkKey;
    private String status;
    private String remediationStatus;
    private String workorderStatus;
    private String expectedValue;
    private String actualValue;
    private String evidence;
    private LocalDateTime scanTime;
}
