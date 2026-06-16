package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PortScanResultEntity {

    private Long id;
    private Long tenantId;
    private String taskId;
    private String hostName;
    private String macAddress;
    private Integer portCount;
    private String portJson;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
