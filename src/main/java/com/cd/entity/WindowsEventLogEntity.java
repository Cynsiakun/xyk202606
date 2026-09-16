package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Windows 事件日志实体，对应 {@code windows_event_logs} 表。
 *
 * <p>幂等键为 {@code (host_id, log_type, record_number)}，由表上唯一索引
 * {@code uk_host_log_rec} 保证同一主机同一日志类型同一记录号只存一条。
 * {@code rawJson} 列存储原始 raw_xml，类型 LONGTEXT，不建索引。</p>
 */
@Data
public class WindowsEventLogEntity {

    private Long id;
    private Long tenantId;
    private Long hostId;
    private String logType;
    private Integer eventId;
    private LocalDateTime eventTime;
    private String username;
    private String level;
    private String message;
    private Long recordNumber;
    private String rawJson;
    private LocalDateTime createTime;
}
