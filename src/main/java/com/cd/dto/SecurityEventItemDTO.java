package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 安全事件列表行，对应 {@code security_alerts} 一条告警（关联 hosts 取主机名）。
 */
@Data
public class SecurityEventItemDTO {

    private Long id;
    private Long sourceLogId;
    private Long hostId;
    private String hostname;
    private String ipv4;
    private Integer eventId;
    private String alertName;
    private String level;
    private Integer riskScore;
    private String description;
    private String status;
    private LocalDateTime eventTime;
    private LocalDateTime createTime;
}
