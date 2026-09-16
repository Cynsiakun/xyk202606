package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.common.license.LicenseSigner;
import com.cd.common.exception.LicenseAccessDeniedException;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.common.security.SecurityUtils;
import com.cd.common.security.TenantContextHolder;
import com.cd.dto.ClientMachineValidateDTO;
import com.cd.dto.ClientMachineValidateResponseDTO;
import com.cd.dto.CsvImportResultDTO;
import com.cd.dto.LicenseDtoConverter;
import com.cd.dto.LicenseOfflineResponseDTO;
import com.cd.dto.TenantMachineCreateDTO;
import com.cd.dto.TenantMachineResponseDTO;
import com.cd.dto.TenantMachineUpdateDTO;
import com.cd.entity.LicenseEntity;
import com.cd.entity.HostEntity;
import com.cd.entity.TenantEntity;
import com.cd.entity.TenantMachineEntity;
import com.cd.mapper.HostMapper;
import com.cd.mapper.LicenseMapper;
import com.cd.mapper.TenantMachineMapper;
import com.cd.mapper.TenantMapper;
import com.cd.service.TenantMachineService;
import com.cd.util.CsvImportUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TenantMachineServiceImpl implements TenantMachineService {

    private final TenantMachineMapper tenantMachineMapper;
    private final TenantMapper tenantMapper;
    private final HostMapper hostMapper;
    private final LicenseMapper licenseMapper;
    private final com.cd.common.license.LicenseGuard licenseGuard;
    private final LicenseSigner licenseSigner;

    @Override
    public PageResult<TenantMachineResponseDTO> list(int page, int size, String keyword) {
        Long tenantId = currentTenantId();
        int offset = (page - 1) * size;
        long total = tenantMachineMapper.countAllByTenant(trim(keyword), tenantId);
        List<TenantMachineResponseDTO> list = tenantMachineMapper.selectPageByTenant(offset, size, trim(keyword), tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(total, list);
    }

    @Override
    @Transactional
    public TenantMachineResponseDTO create(TenantMachineCreateDTO dto) {
        Long tenantId = currentTenantId();
        requireMac(dto.getMacAddress());
        ensureUnique(null, null, dto.getMacAddress());

        TenantMachineEntity entity = new TenantMachineEntity();
        entity.setTenantId(tenantId);
        entity.setMachineId(null);
        entity.setMacAddress(normalizeMac(dto.getMacAddress()));
        entity.setHostName(trim(dto.getHostName()));
        entity.setRemark(trim(dto.getRemark()));
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        entity.setCreatedBy(SecurityUtils.getCurrentUserId());
        tenantMachineMapper.insert(entity);
        syncAuthorizedHostTenant(entity.getTenantId(), entity.getMacAddress(), entity.getHostName());
        return toResponse(tenantMachineMapper.selectByIdAndTenant(entity.getId(), tenantId));
    }

    @Override
    @Transactional
    public TenantMachineResponseDTO update(Long id, TenantMachineUpdateDTO dto) {
        Long tenantId = currentTenantId();
        TenantMachineEntity existing = ensureExists(id, tenantId);
        requireMac(dto.getMacAddress());
        ensureUnique(id, existing.getMachineId(), dto.getMacAddress());
        int nextStatus = dto.getStatus() == null ? existing.getStatus() : dto.getStatus();

        existing.setMacAddress(normalizeMac(dto.getMacAddress()));
        existing.setHostName(trim(dto.getHostName()));
        existing.setRemark(trim(dto.getRemark()));
        existing.setStatus(nextStatus);
        tenantMachineMapper.updateByIdAndTenant(existing);
        if (nextStatus == 1) {
            syncAuthorizedHostTenant(existing.getTenantId(), existing.getMacAddress(), existing.getHostName());
        }
        return toResponse(tenantMachineMapper.selectByIdAndTenant(id, tenantId));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long tenantId = currentTenantId();
        ensureExists(id, tenantId);
        tenantMachineMapper.deleteByIdAndTenant(id, tenantId);
    }

    @Override
    @Transactional
    public CsvImportResultDTO importCsv(MultipartFile file) {
        return CsvImportUtil.importCsv(file, this::mapCsvRecord, this::saveImportedRecord);
    }

    @Override
    public List<TenantMachineResponseDTO> activatedHosts() {
        Long tenantId = currentTenantId();
        return tenantMachineMapper.selectActivatedByTenant(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public long countActivatedByTenant() {
        return tenantMachineMapper.countActivatedByTenant(currentTenantId());
    }

    @Override
    @Transactional
    public ClientMachineValidateResponseDTO validate(ClientMachineValidateDTO dto) {
        String machineId = trim(dto.getMachineId());
        String macAddress = normalizeMac(dto.getMacAddress());
        requireMac(macAddress);

        TenantMachineEntity machine = tenantMachineMapper.selectActiveByMacAddress(macAddress);
        if (machine == null) {
            return deny("MACHINE_NOT_AUTHORIZED");
        }
        String boundMachineId = trim(machine.getMachineId());
        if (StringUtils.hasText(boundMachineId)) {
            if (!boundMachineId.equals(machineId)) {
                return deny("MACHINE_ID_MISMATCH");
            }
        } else if (StringUtils.hasText(machineId)) {
            requireMachineQuota(machine.getTenantId(), 1);
            tenantMachineMapper.bindMachineIdIfEmpty(machine.getId(), machineId);
            machine = tenantMachineMapper.selectByIdAndTenant(machine.getId(), machine.getTenantId());
        }
        TenantEntity tenant = tenantMapper.selectById(machine.getTenantId());
        if (tenant == null || tenant.getStatus() == null || tenant.getStatus() != 1) {
            return deny("TENANT_DISABLED");
        }
        LicenseEntity license = licenseMapper.selectEffectiveByTenantId(machine.getTenantId());
        if (license == null) {
            return deny("NO_EFFECTIVE_LICENSE");
        }
        syncAuthorizedHostTenant(machine.getTenantId(), machine.getMacAddress(), machine.getHostName());

        ClientMachineValidateResponseDTO response = new ClientMachineValidateResponseDTO();
        LicenseEntity signedLicense = buildSignedLicenseView(license, machine.getMachineId());
        LicenseOfflineResponseDTO offlineResponse = LicenseDtoConverter.toOfflineResponse(
                signedLicense,
                licenseGuard.featureNames(license.getEdition())
        );
        response.setAllowed(true);
        response.setAuthorized(true);
        response.setReason("OK");
        response.setTenantId(machine.getTenantId());
        response.setTenantName(tenant.getName());
        response.setEdition(license.getEdition());
        response.setLicenseKey(signedLicense.getLicenseKey());
        response.setHostLimit(signedLicense.getHostLimit());
        response.setUserLimit(signedLicense.getUserLimit());
        response.setMachineId(signedLicense.getMachineId());
        response.setExpireTime(license.getExpireTime());
        response.setPayload(offlineResponse.getPayload());
        response.setSignature(offlineResponse.getSignature());
        response.setFeatureFlags(offlineResponse.getPayload().getFeatureFlags());
        return response;
    }

    @Override
    public void requireMachineQuotaForActivation(Long tenantId) {
        requireMachineQuota(tenantId, 1);
    }

    @Override
    public void syncAuthorizedHostTenantForActivation(Long tenantId, String macAddress, String hostName) {
        syncAuthorizedHostTenant(tenantId, macAddress, hostName);
    }

    private TenantMachineEntity ensureExists(Long id, Long tenantId) {
        TenantMachineEntity entity = tenantMachineMapper.selectByIdAndTenant(id, tenantId);
        if (entity == null) {
            throw new ResourceNotFoundException("Machine not found: id=" + id);
        }
        return entity;
    }

    private void requireMachineQuota(Long tenantId, int increment) {
        if (tenantId == 0L) {
            return;
        }
        LicenseEntity license = licenseMapper.selectEffectiveByTenantId(tenantId);
        if (license == null) {
            throw new LicenseAccessDeniedException("No effective License for current tenant");
        }
        int limit = licenseGuard.effectiveHostLimit(license);
        if (limit == 0) {
            return;
        }
        long current = tenantMachineMapper.countActivatedByTenant(tenantId);
        if (current + Math.max(1, increment) > limit) {
            throw new LicenseAccessDeniedException("Host quota exceeded");
        }
    }

    private void ensureUnique(Long id, String machineId, String macAddress) {
        String normalizedMachineId = trim(machineId);
        if (normalizedMachineId != null) {
            TenantMachineEntity existing = tenantMachineMapper.selectByMachineId(normalizedMachineId);
            if (existing != null && !existing.getId().equals(id)) {
                throw new IllegalArgumentException("machineId already exists");
            }
        }
        String normalizedMac = normalizeMac(macAddress);
        if (normalizedMac != null) {
            TenantMachineEntity existing = tenantMachineMapper.selectByMacAddress(normalizedMac);
            if (existing != null && !existing.getId().equals(id)) {
                throw new IllegalArgumentException("macAddress already exists");
            }
        }
    }

    private void requireMac(String macAddress) {
        if (!StringUtils.hasText(macAddress)) {
            throw new IllegalArgumentException("macAddress is required");
        }
    }

    private ClientMachineValidateResponseDTO deny(String reason) {
        ClientMachineValidateResponseDTO response = new ClientMachineValidateResponseDTO();
        response.setAllowed(false);
        response.setAuthorized(false);
        response.setReason(reason);
        response.setFeatureFlags(List.of());
        return response;
    }

    private void syncAuthorizedHostTenant(Long tenantId, String macAddress, String hostName) {
        String normalizedMac = normalizeMacKey(macAddress);
        if (!StringUtils.hasText(normalizedMac)) {
            return;
        }
        int claimed = hostMapper.claimTenantByNormalizedMac(normalizedMac, tenantId, trim(hostName));
        if (claimed > 0) {
            return;
        }
        HostEntity host = new HostEntity();
        host.setTenantId(tenantId);
        host.setHostname(trim(hostName));
        host.setMacAddress(macAddress);
        host.setStatus(1);
        hostMapper.insertAuthorizedPlaceholder(host.getTenantId(), host.getHostname(), host.getMacAddress());
    }

    private TenantMachineEntity mapCsvRecord(CSVRecord record) {
        TenantMachineEntity entity = new TenantMachineEntity();
        entity.setMacAddress(requireField(record, "MAC地址", "macAddress", "mac_address", "MAC", "mac"));
        entity.setHostName(trim(CsvImportUtil.getValue(record, "主机名", "hostName", "host_name", "hostname")));
        entity.setStatus(1);
        return entity;
    }

    private void saveImportedRecord(TenantMachineEntity imported, CsvImportResultDTO result) {
        Long tenantId = currentTenantId();
        String normalizedMac = normalizeMac(imported.getMacAddress());
        imported.setMacAddress(normalizedMac);

        TenantMachineEntity existing = tenantMachineMapper.selectByMacAddress(normalizedMac);
        if (existing == null) {
            imported.setTenantId(tenantId);
            imported.setMachineId(null);
            imported.setRemark(null);
            imported.setCreatedBy(SecurityUtils.getCurrentUserId());
            tenantMachineMapper.insert(imported);
            syncAuthorizedHostTenant(tenantId, imported.getMacAddress(), imported.getHostName());
            result.incrementInserted();
            return;
        }

        if (!tenantId.equals(existing.getTenantId())) {
            throw new IllegalArgumentException("macAddress already exists");
        }
        existing.setMacAddress(normalizedMac);
        existing.setHostName(imported.getHostName());
        existing.setStatus(1);
        tenantMachineMapper.updateByIdAndTenant(existing);
        syncAuthorizedHostTenant(tenantId, existing.getMacAddress(), existing.getHostName());
        result.incrementUpdated();
    }

    private LicenseEntity buildSignedLicenseView(LicenseEntity license, String machineId) {
        LicenseEntity signed = new LicenseEntity();
        signed.setId(license.getId());
        signed.setLicenseKey(license.getLicenseKey());
        signed.setTenantId(license.getTenantId());
        signed.setEdition(license.getEdition());
        signed.setHostLimit(licenseGuard.effectiveHostLimit(license));
        signed.setUserLimit(licenseGuard.effectiveUserLimit(license));
        signed.setExpireTime(license.getExpireTime());
        signed.setMachineId(machineId);
        signed.setStatus(license.getStatus());
        signed.setCreatedAt(license.getCreatedAt());
        signed.setSignature(licenseSigner.sign(signed));
        return signed;
    }

    private TenantMachineResponseDTO toResponse(TenantMachineEntity entity) {
        TenantMachineResponseDTO dto = new TenantMachineResponseDTO();
        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setTenantName(entity.getTenantName());
        dto.setMachineId(entity.getMachineId());
        dto.setMacAddress(entity.getMacAddress());
        dto.setHostName(entity.getHostName());
        dto.setRemark(entity.getRemark());
        dto.setStatus(entity.getStatus());
        dto.setMachineBoundAt(resolveMachineBoundAt(entity));
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private LocalDateTime resolveMachineBoundAt(TenantMachineEntity entity) {
        if (entity.getMachineBoundAt() != null) {
            return entity.getMachineBoundAt();
        }
        if (!StringUtils.hasText(entity.getMachineId())) {
            return null;
        }
        if (entity.getUpdatedAt() != null) {
            return entity.getUpdatedAt();
        }
        return entity.getCreatedAt();
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String requireField(CSVRecord record, String... headerNames) {
        String value = CsvImportUtil.getValue(record, headerNames);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("missing required field: " + headerNames[0]);
        }
        return value.trim();
    }

    private String normalizeMac(String value) {
        String trimmed = trim(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT).replace(':', '-');
    }

    private String normalizeMacKey(String value) {
        String trimmed = trim(value);
        if (trimmed == null) {
            return "";
        }
        return trimmed.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-f]", "");
    }
}
