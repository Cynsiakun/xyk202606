package com.cd.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PatchCveMapResponseDTO {

    private Long id;
    private String patchId;
    private String cveId;
    private String vendor;
    private String product;
    private String affectedVersionRange;
    private String fixedVersion;
    private String fixType;
    private String exploitStatus;
    private Integer kevFlag;
    private BigDecimal cvssScore;
    private String severity;
    private String referenceUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
