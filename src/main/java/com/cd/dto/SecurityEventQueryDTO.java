package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 安全事件查询条件。全部筛选在服务端完成，禁止前端全量过滤。
 * {@code sortColumn} 已由服务层白名单化，可直接拼入 ORDER BY。
 */
@Data
public class SecurityEventQueryDTO {

    private String level;       // Critical/High/Medium，空=全部
    private String status;      // new/acked/resolved，空=全部
    private String keyword;     // 主机名 / 描述 关键字
    private Integer eventId;
    private Long hostId;
    private String alertName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long tenantId;

    private String sortColumn = "event_time";
    private String sortDirection = "DESC";

    private int offset;
    private int size = 20;
}
