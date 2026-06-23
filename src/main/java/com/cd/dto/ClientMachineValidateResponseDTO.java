package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ClientMachineValidateResponseDTO {

    private Boolean allowed;
    private Boolean authorized;
    private String reason;
    private Long tenantId;
    private String tenantName;
    private String edition;
    private String licenseKey;
    private Integer hostLimit;
    private Integer userLimit;
    private String machineId;
    private LocalDateTime expireTime;
    private LicensePayloadDTO payload;
    private String signature;
    private List<String> featureFlags;
}
