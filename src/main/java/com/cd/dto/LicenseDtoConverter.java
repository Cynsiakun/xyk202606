package com.cd.dto;

import com.cd.entity.LicenseEntity;

public final class LicenseDtoConverter {

    private LicenseDtoConverter() {
    }

    public static LicenseResponseDTO toResponse(LicenseEntity entity) {
        LicenseResponseDTO dto = new LicenseResponseDTO();
        dto.setId(entity.getId());
        dto.setLicenseKey(entity.getLicenseKey());
        dto.setTenantId(entity.getTenantId());
        dto.setEdition(entity.getEdition());
        dto.setHostLimit(entity.getHostLimit());
        dto.setUserLimit(entity.getUserLimit());
        dto.setExpireTime(entity.getExpireTime());
        dto.setMachineId(entity.getMachineId());
        dto.setSignature(entity.getSignature());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public static LicenseOfflineResponseDTO toOfflineResponse(LicenseEntity entity) {
        LicensePayloadDTO payload = new LicensePayloadDTO();
        payload.setLicenseKey(entity.getLicenseKey());
        payload.setTenantId(entity.getTenantId());
        payload.setEdition(entity.getEdition());
        payload.setHostLimit(entity.getHostLimit());
        payload.setUserLimit(entity.getUserLimit());
        payload.setExpireTime(entity.getExpireTime());
        payload.setMachineId(entity.getMachineId());

        LicenseOfflineResponseDTO dto = new LicenseOfflineResponseDTO();
        dto.setPayload(payload);
        dto.setSignature(entity.getSignature());
        return dto;
    }
}
