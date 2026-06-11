package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 安全事件详情：告警本体 + 关联原始日志（{@code windows_event_logs}）的摘要与原始 XML。
 * 当 source_log_id 对应的原始日志不存在时，{@code logMessage}/{@code rawXml} 为空。
 */
@Data
public class SecurityEventDetailDTO {

    private Long id;
    private Long sourceLogId;
    private Long hostId;
    private String hostname;
    private String ipv4;
    private Integer eventId;
    private String ruleCode;
    private String alertName;
    private String level;
    private Integer riskScore;
    private String description;
    private String evidenceJson;
    private String status;
    private LocalDateTime eventTime;
    private LocalDateTime createTime;

    // 关联原始日志（如可获取）
    private String logType;
    private String logLevel;
    private String logUsername;
    private String logMessage;
    private String rawXml;
}
