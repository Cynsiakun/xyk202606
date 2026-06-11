package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账户变更日志列表行，对应 {@code account_change_logs} 一条记录（已 join 主机）。
 */
@Data
public class AccountChangeLogItemDTO {

    private Long id;
    private Long sourceLogId;
    private Long hostId;
    private String hostname;
    private String ipv4;
    private Integer eventId;
    private LocalDateTime eventTime;
    private String operatorUsername;
    private String targetUsername;
    /** create / enable / change_password / delete / add_to_group / modify。 */
    private String actionType;
    private String details;
}
