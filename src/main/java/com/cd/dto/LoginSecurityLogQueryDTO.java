package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志查询条件。所有筛选均在服务端完成，禁止前端全量过滤。
 * {@code sortColumn} 已由服务层白名单化，可直接拼入 ORDER BY。
 */
@Data
public class LoginSecurityLogQueryDTO {

    private String loginResult;
    private String keyword;
    private Integer eventId;
    private Long hostId;
    private String username;
    private String sourceIp;
    private Integer loginType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long tenantId;

    private String sortColumn = "event_time";
    private String sortDirection = "DESC";

    private int offset;
    private int size = 20;
}
