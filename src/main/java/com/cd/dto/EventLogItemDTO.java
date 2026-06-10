package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 日志中心列表行，对应 {@code windows_event_logs} 一条原始日志。
 * 不含 {@code rawJson}，避免列表查询拖出超大 LONGTEXT。
 */
@Data
public class EventLogItemDTO {

    private Long id;
    private Long hostId;
    private String hostname;
    private String ipv4;
    private String logType;
    private Integer eventId;
    private LocalDateTime eventTime;
    private String username;
    private String level;
    private String message;
    private Long recordNumber;
}
