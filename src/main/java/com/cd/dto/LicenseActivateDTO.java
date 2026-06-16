package com.cd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LicenseActivateDTO {

    @NotBlank(message = "licenseKey must not be blank")
    @Size(max = 64, message = "licenseKey length must be less than or equal to 64")
    private String licenseKey;

    @NotBlank(message = "machineId must not be blank")
    @Size(max = 128, message = "machineId length must be less than or equal to 128")
    private String machineId;
}
