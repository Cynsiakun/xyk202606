package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TenantMachineEntity {

    private Long id;
    private Long tenantId;
    private String tenantName;
    private String machineId;
    private String macAddress;
    private String hostName;
    private String remark;
    private Integer status;
    private Long createdBy;
    private LocalDateTime machineBoundAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
