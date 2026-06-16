package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class LicenseCurrentDTO {

    private Long tenantId;
    private String edition;
    private Integer hostLimit;
    private Long hostUsed;
    private Integer userLimit;
    private Long userUsed;
    private LocalDateTime expireTime;
    private Integer status;
    private Boolean effective;
    private String message;
    private List<String> featureFlags;
}
