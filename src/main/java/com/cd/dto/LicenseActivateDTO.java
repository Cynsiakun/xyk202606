package com.cd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LicenseActivateDTO {

    @Size(max = 64, message = "licenseKey length must be less than or equal to 64")
    private String licenseKey;

    @Size(max = 64, message = "activationCode length must be less than or equal to 64")
    private String activationCode;

    @NotBlank(message = "machineId must not be blank")
    @Size(max = 128, message = "machineId length must be less than or equal to 128")
    private String machineId;

    @Size(max = 64, message = "macAddress length must be less than or equal to 64")
    private String macAddress;

    @Size(max = 255, message = "hostName length must be less than or equal to 255")
    private String hostName;
}
