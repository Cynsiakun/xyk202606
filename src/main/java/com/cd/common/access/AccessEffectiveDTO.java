package com.cd.common.access;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AccessEffectiveDTO {

    private Long tenantId;
    private String tenantName;
    private String edition;
    private Boolean effective;
    private String message;
    private LocalDateTime expireTime;
    private List<String> tenantFeatures;
    private List<String> platformFeatures;
    private List<String> allowedPolicies;
    private Long hostUsed;
    private Integer hostLimit;
    private Long userUsed;
    private Integer userLimit;
}
