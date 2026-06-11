package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账户变更日志详情，含关联的来源原始日志（{@code windows_event_logs} 的 message 与 raw_xml）。
 */
@Data
public class AccountChangeLogDetailDTO {

    private Long id;
    private Long sourceLogId;
    private Long hostId;
    private String hostname;
    private String ipv4;
    private Integer eventId;
    private LocalDateTime eventTime;
    private String operatorUsername;
    private String targetUsername;
    private String actionType;
    private String details;
    private LocalDateTime createTime;

    /** 来源原始日志摘要（可能为空）。 */
    private String logMessage;
    /** 来源原始日志 XML（可能为空）。 */
    private String rawXml;
}
