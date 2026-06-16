package com.cd.dto;

import lombok.Data;

@Data
public class LicenseOfflineResponseDTO {

    private LicensePayloadDTO payload;
    private String signature;
}
