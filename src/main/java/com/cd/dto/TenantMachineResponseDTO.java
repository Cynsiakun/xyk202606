package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TenantMachineResponseDTO {

    private Long id;
    private Long tenantId;
    private String tenantName;
    private String machineId;
    private String macAddress;
    private String hostName;
    private String remark;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
