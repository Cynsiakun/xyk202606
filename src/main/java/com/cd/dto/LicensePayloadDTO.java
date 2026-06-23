package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class LicensePayloadDTO {

    private String licenseKey;
    private Long tenantId;
    private String edition;
    private Integer hostLimit;
    private Integer userLimit;
    private LocalDateTime expireTime;
    private String machineId;
    private List<String> featureFlags;
}
