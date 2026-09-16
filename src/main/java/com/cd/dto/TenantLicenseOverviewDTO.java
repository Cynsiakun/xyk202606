package com.cd.dto;

import lombok.Data;

import java.util.List;

@Data
public class TenantLicenseOverviewDTO {

    private LicenseCurrentDTO currentLicense;
    private Long activatedHostCount;
    private Integer remainingActivatableCount;
    private List<TenantMachineResponseDTO> activatedHosts;
    private List<ActivationCodeResponseDTO> activationCodes;
}
