package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActivationCodeEntity {

    private Long id;
    private Long tenantId;
    private Long licenseId;
    private String code;
    private String status;
    private LocalDateTime expireTime;
    private String boundMachineId;
    private String boundMacAddress;
    private String boundHostName;
    private LocalDateTime usedAt;
    private Long createdBy;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
