package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ActivationCodeResponseDTO {

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
    private String remark;
    private LocalDateTime createdAt;
}
