package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录安全日志，对应 {@code login_security_logs} 表。
 *
 * <p>由 Windows 安全事件 4624/4625/4634/4647 分流解析而来。{@code sourceLogId} 指向
 * 来源的 {@code windows_event_logs.id}，并由唯一键 {@code uk_source_log} 保证重复消费不产生重复行。</p>
 */
@Data
public class LoginSecurityLogEntity {

    private Long id;
    /** 来源 windows_event_logs.id，去重键。 */
    private Long sourceLogId;
    private Long hostId;
    private Integer eventId;
    private LocalDateTime eventTime;
    private String username;
    /** success / fail / logout。 */
    private String loginResult;
    /** Windows LogonType：2 交互、3 网络、10 RDP、5 服务等。 */
    private Integer loginType;
    private String sourceIp;
    private String processName;
    /** 是否提权（ElevatedToken）。 */
    private Integer isElevated;
    private LocalDateTime createTime;
}
