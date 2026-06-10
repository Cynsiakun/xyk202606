package com.cd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InstalledPatchCreateDTO {

    @NotNull(message = "host_id 不能为空")
    private Long hostId;

    @NotBlank(message = "patch_id 不能为空")
    @Size(max = 128, message = "patch_id 长度不能超过128")
    private String patchId;

    @Size(max = 64, message = "patch_type 长度不能超过64")
    private String patchType;

    @Size(max = 255, message = "product_name 长度不能超过255")
    private String productName;

    @Size(max = 128, message = "product_version 长度不能超过128")
    private String productVersion;

    private LocalDateTime installTime;

    @Size(max = 64, message = "install_status 长度不能超过64")
    private String installStatus;

    @Size(max = 64, message = "source 长度不能超过64")
    private String source;

    @Size(max = 32, message = "signature_status 长度不能超过32")
    private String signatureStatus;

    private Integer rebootRequired;

    @Size(max = 128, message = "superseded_by 长度不能超过128")
    private String supersededBy;

    private Integer isSecurityPatch;

    private String rawData;

    private LocalDateTime scanTime;
}
