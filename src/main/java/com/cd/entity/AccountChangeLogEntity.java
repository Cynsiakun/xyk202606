package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账户变更日志，对应 {@code account_change_logs} 表。
 *
 * <p>由 Windows 安全事件 4720/4722/4723/4726/4728/4738 分流解析而来。{@code sourceLogId}
 * 指向来源的 {@code windows_event_logs.id}，由唯一键 {@code uk_source_log} 保证重复消费不产生重复行。</p>
 */
@Data
public class AccountChangeLogEntity {

    private Long id;
    private Long tenantId;
    /** 来源 windows_event_logs.id，去重键。 */
    private Long sourceLogId;
    private Long hostId;
    private Integer eventId;
    private LocalDateTime eventTime;
    /** 操作者（SubjectUserName）。 */
    private String operatorUsername;
    /** 目标用户（TargetUserName）。 */
    private String targetUsername;
    /** create / enable / change_password / delete / add_to_group / modify。 */
    private String actionType;
    private String details;
    private LocalDateTime createTime;
}
