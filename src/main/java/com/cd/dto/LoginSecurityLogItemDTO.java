package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录日志列表行，对应 {@code login_security_logs} 一条记录（已 join 主机）。
 */
@Data
public class LoginSecurityLogItemDTO {

    private Long id;
    private Long sourceLogId;
    private Long hostId;
    private String hostname;
    private String ipv4;
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
}
