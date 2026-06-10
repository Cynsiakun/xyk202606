package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 日志中心详情，含原始 XML（{@code rawJson} 列，存的是完整 raw_xml）。
 */
@Data
public class EventLogDetailDTO {

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
    private String rawXml;
    private LocalDateTime createTime;
}
