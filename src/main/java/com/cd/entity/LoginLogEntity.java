package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginLogEntity {

    private Integer id;
    private Long tenantId;
    private Long userId;
    private String userName;
    private LocalDateTime loginTime;
    private String ipAddress;
    private Integer status;
    private String message;
}
