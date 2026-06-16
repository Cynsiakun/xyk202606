package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LicenseResponseDTO {

    private Long id;
    private String licenseKey;
    private Long tenantId;
    private String edition;
    private Integer hostLimit;
    private Integer userLimit;
    private LocalDateTime expireTime;
    private String machineId;
    private String signature;
    private Integer status;
    private LocalDateTime createdAt;
}
