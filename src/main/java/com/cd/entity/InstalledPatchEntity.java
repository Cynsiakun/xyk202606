package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InstalledPatchEntity {

    private Long id;
    private Long hostId;
    private String patchId;
    private String patchType;
    private String productName;
    private String productVersion;
    private LocalDateTime installTime;
    private String installStatus;
    private String source;
    private String signatureStatus;
    private Integer rebootRequired;
    private String supersededBy;
    private Integer isSecurityPatch;
    private String rawData;
    private LocalDateTime scanTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
