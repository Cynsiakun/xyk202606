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
    private Integer hostLimit;
    private LocalDateTime expireTime;
    private List<String> featureFlags;
}
