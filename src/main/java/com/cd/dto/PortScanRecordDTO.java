package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PortScanRecordDTO {

    private Long id;
    private String taskId;
    private String hostName;
    private String macAddress;
    private Integer portCount;
    private String portJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
