package com.cd.dto;

import com.cd.entity.LicenseEntity;

import java.util.List;

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
        return toOfflineResponse(entity, null);
    }

    public static LicenseOfflineResponseDTO toOfflineResponse(LicenseEntity entity, List<String> featureFlags) {
        LicensePayloadDTO payload = new LicensePayloadDTO();
        payload.setLicenseKey(entity.getLicenseKey());
        payload.setTenantId(entity.getTenantId());
        payload.setEdition(entity.getEdition());
        payload.setHostLimit(entity.getHostLimit());
        payload.setUserLimit(entity.getUserLimit());
        payload.setExpireTime(entity.getExpireTime());
        payload.setMachineId(entity.getMachineId());
        payload.setFeatureFlags(featureFlags);

        LicenseOfflineResponseDTO dto = new LicenseOfflineResponseDTO();
        dto.setPayload(payload);
        dto.setSignature(entity.getSignature());
        return dto;
    }
}
