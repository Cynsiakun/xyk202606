package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentResultOperationEntity {

    private Long id;
    private Long tenantId;
    private String operationId;
    private String type;
    private String hostName;
    private String macAddress;
    private String status;
    private String requestJson;
    private String resultJson;
    private String rawMessage;
    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;
    private LocalDateTime receivedAt;
}
