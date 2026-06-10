package com.cd.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PatchCveMapUpdateDTO {

    @NotBlank(message = "patchId不能为空")
    private String patchId;

    @NotBlank(message = "cveId不能为空")
    private String cveId;

    private String vendor;
    private String product;
    private String affectedVersionRange;
    private String fixedVersion;
    private String fixType;
    private String exploitStatus;
    private Integer kevFlag;

    @DecimalMin(value = "0.0", message = "cvssScore不能小于0")
    @DecimalMax(value = "10.0", message = "cvssScore不能大于10")
    private BigDecimal cvssScore;

    private String severity;
    private String referenceUrl;
}
