package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 安全告警，对应 {@code security_alerts} 表。
 */
@Data
public class SecurityAlertEntity {

    private Long id;
    private Long sourceLogId;
    private Long hostId;
    private Integer eventId;
    private String ruleCode;
    private String dedupKey;
    private String alertName;
    private String level;
    private Integer riskScore;
    private String description;
    private String evidenceJson;
    private String status;
    private LocalDateTime eventTime;
    private LocalDateTime createTime;
}
