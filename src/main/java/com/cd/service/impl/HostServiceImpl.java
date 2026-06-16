package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.common.license.LicenseFeature;
import com.cd.common.license.LicenseGuard;
import com.cd.common.config.RabbitMQConfig;
import com.cd.common.exception.ProbeConfirmRequiredException;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.common.security.TenantContextHolder;
import com.cd.dto.AssetProbeDTO;
import com.cd.dto.PortScanDTO;
import com.cd.dto.CsvImportResultDTO;
import com.cd.dto.HostCreateDTO;
import com.cd.dto.HostResponseDTO;
import com.cd.dto.HostUpdateDTO;
import com.cd.entity.HostEntity;
import com.cd.mapper.HostMapper;
import com.cd.service.HostService;
import com.cd.service.ProbeStrategyService;
import com.cd.util.CsvImportUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVRecord;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HostServiceImpl implements HostService {

    private static final int ONLINE_THRESHOLD_SECONDS = 4;
    private static final String PROBE_TYPE_ASSETS = "assets";
    private static final String PROBE_TYPE_PORT_SCAN = "port_scan";
    private static final int PROBE_ONLINE_THRESHOLD_SECONDS = 15;
    private static final Duration MANUAL_PROBE_VALID_DURATION = Duration.ofHours(8);
    private static final List<DateTimeFormatter> CSV_DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    );

    private final HostMapper hostMapper;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final AmqpAdmin amqpAdmin;
    private final LicenseGuard licenseGuard;
    private final ProbeStrategyService probeStrategyService;

    @Override
    public HostResponseDTO create(HostCreateDTO dto) {
        validateMacUnique(null, dto.getMacAddress());
        Long tenantId = currentTenantId();
        licenseGuard.requireFeature(LicenseFeature.HOST);
        licenseGuard.requireHostQuotaBeforeCreate();
        HostEntity entity = new HostEntity();
        entity.setTenantId(tenantId);
        entity.setHostname(dto.getHostname());
        entity.setIpv4(dto.getIpv4());
        entity.setMacAddress(dto.getMacAddress());
        entity.setOsName(dto.getOsName());
        entity.setOsVersion(dto.getOsVersion());
        entity.setOsArch(dto.getOsArch());
        entity.setOsRelease(dto.getOsRelease());
        entity.setCpuModel(dto.getCpuModel());
        entity.setCpuPhysicalCores(dto.getCpuPhysicalCores());
        entity.setCpuLogicalCores(dto.getCpuLogicalCores());
        entity.setMemTotal(dto.getMemTotal());
        entity.setMemUsed(dto.getMemUsed());
        entity.setMemAvailable(dto.getMemAvailable());
        entity.setMemUsage(dto.getMemUsage());
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        hostMapper.insert(entity);
        return toResponse(hostMapper.selectByIdAndTenant(entity.getId(), tenantId));
    }

    @Override
    public HostResponseDTO update(Long id, HostUpdateDTO dto) {
        licenseGuard.requireFeature(LicenseFeature.HOST);
        Long tenantId = currentTenantId();
        HostEntity existing = ensureExists(id);
        validateMacUnique(id, dto.getMacAddress());
        existing.setHostname(dto.getHostname());
        existing.setIpv4(dto.getIpv4());
        existing.setMacAddress(dto.getMacAddress());
        existing.setOsName(dto.getOsName());
        existing.setOsVersion(dto.getOsVersion());
        existing.setOsArch(dto.getOsArch());
        existing.setOsRelease(dto.getOsRelease());
        existing.setCpuModel(dto.getCpuModel());
        existing.setCpuPhysicalCores(dto.getCpuPhysicalCores());
        existing.setCpuLogicalCores(dto.getCpuLogicalCores());
        existing.setMemTotal(dto.getMemTotal());
        existing.setMemUsed(dto.getMemUsed());
        existing.setMemAvailable(dto.getMemAvailable());
        existing.setMemUsage(dto.getMemUsage());
        existing.setStatus(dto.getStatus() == null ? existing.getStatus() : dto.getStatus());
        hostMapper.updateById(existing);
        return toResponse(hostMapper.selectByIdAndTenant(id, tenantId));
    }

    @Override
    public void deleteById(Long id) {
        licenseGuard.requireFeature(LicenseFeature.HOST);
        Long tenantId = currentTenantId();
        ensureExists(id);
        hostMapper.deleteByIdAndTenant(id, tenantId);
    }

    @Override
    public HostResponseDTO getById(Long id) {
        return toResponse(ensureExists(id));
    }

    @Override
    public PageResult<HostResponseDTO> list(int page, int size, String keyword) {
        hostMapper.reconcileStatusByHeartbeat(ONLINE_THRESHOLD_SECONDS);
        int offset = (page - 1) * size;
        String normalizedKeyword = emptyToNull(keyword);
        Long tenantId = currentTenantId();
        long total = hostMapper.countAllByTenant(normalizedKeyword, tenantId);
        List<HostResponseDTO> list = hostMapper.selectPageByTenant(offset, size, normalizedKeyword, tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(total, list);
    }

    @Override
    @Transactional
    public CsvImportResultDTO importCsv(MultipartFile file) {
        return CsvImportUtil.importCsv(file, this::mapCsvRecord, this::saveImportedRecord);
    }

    @Override
    public void saveOrUpdateFromMessage(HostEntity entity) {
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        hostMapper.upsertByMac(entity);
    }

    @Override
    public void heartbeat(String macAddress) {
        hostMapper.heartbeatByMac(macAddress);
    }

    @Override
    public int markOfflineHosts(int offlineThresholdSeconds) {
        return hostMapper.markOffline(offlineThresholdSeconds);
    }

    @Override
    public int autoProbeOnlineHosts(int limit) {
        return autoProbeOnlineHosts(limit, true, true, true, true);
    }

    @Override
    public int autoProbeOnlineHosts(int limit, boolean account, boolean service, boolean process, boolean app) {
        int batchSize = Math.max(1, limit);
        List<HostEntity> hosts = hostMapper.selectAutoProbeCandidates(batchSize);
        int sentCount = 0;
        for (HostEntity host : hosts) {
            if (host == null || !StringUtils.hasText(host.getMacAddress())) {
                continue;
            }
            AssetProbeDTO dto = new AssetProbeDTO();
            dto.setAccount(account);
            dto.setService(service);
            dto.setProcess(process);
            dto.setApp(app);
            dto.setMacAddress(host.getMacAddress());
            dto.setForce(true);
            try {
                sendAssetProbeInternal(dto, false);
                sentCount++;
            } catch (Exception e) {
                log.warn("自动资产探测下发失败: hostId={}, mac={}, reason={}",
                        host.getId(), host.getMacAddress(), e.getMessage());
            }
        }
        return sentCount;
    }

    @Override
    public void sendAssetProbe(AssetProbeDTO dto) {
        licenseGuard.requireFeature(LicenseFeature.ASSET);
        String macAddress = dto.getMacAddress();
        if (!StringUtils.hasText(macAddress)) {
            throw new IllegalArgumentException("MAC地址不能为空");
        }

        HostEntity host = hostMapper.selectByMacAndTenant(macAddress, currentTenantId());
        if (!dto.isForce()
                && host != null
                && host.getLastScanTime() != null
                && Duration.between(host.getLastScanTime(), LocalDateTime.now()).compareTo(MANUAL_PROBE_VALID_DURATION) < 0) {
            throw new ProbeConfirmRequiredException("该主机资产数据仍在有效期内，是否继续探测？");
        }

        sendAssetProbeInternal(dto, true);
    }

    private void sendAssetProbeInternal(AssetProbeDTO dto, boolean strictOnlineCheck) {
        String macAddress = dto.getMacAddress();
        HostEntity host = strictOnlineCheck
                ? hostMapper.selectByMacAndTenant(macAddress, currentTenantId())
                : hostMapper.selectByMac(macAddress);
        LocalDateTime updatedAt = host == null ? null : host.getUpdatedAt();
        if (strictOnlineCheck && (updatedAt == null
                || Duration.between(updatedAt, LocalDateTime.now()).getSeconds() > PROBE_ONLINE_THRESHOLD_SECONDS)) {
            throw new IllegalArgumentException("主机已下线，无法执行资产探测");
        }

        String queueName = RabbitMQConfig.AGENT_QUEUE_PREFIX + normalizeMac(macAddress) + RabbitMQConfig.AGENT_QUEUE_SUFFIX;
        if (amqpAdmin.getQueueProperties(queueName) == null) {
            throw new IllegalArgumentException("客户端队列不存在，无法发送探测指令");
        }

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("account", dto.isAccount() ? 1 : 0);
        message.put("service", dto.isService() ? 1 : 0);
        message.put("process", dto.isProcess() ? 1 : 0);
        message.put("app", dto.isApp() ? 1 : 0);
        message.put("macAddress", macAddress);
        message.put("type", PROBE_TYPE_ASSETS);

        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new IllegalStateException("组装资产探测消息失败", e);
        }

        rabbitTemplate.convertAndSend(RabbitMQConfig.AGENT_EXCHANGE, macAddress, payload);
        log.info("资产探测任务已下发: routingKey={}, payload={}", macAddress, payload);
    }

    private HostEntity mapCsvRecord(CSVRecord record) {
        HostEntity entity = new HostEntity();
        entity.setHostname(emptyToNull(CsvImportUtil.getValue(record, "hostname", "host_name")));
        entity.setIpv4(emptyToNull(CsvImportUtil.getValue(record, "ipv4", "ip")));
        entity.setMacAddress(requireField(record, "mac_address", "macAddress"));
        entity.setOsName(emptyToNull(CsvImportUtil.getValue(record, "os_name", "osName")));
        entity.setOsVersion(emptyToNull(CsvImportUtil.getValue(record, "os_version", "osVersion")));
        entity.setOsArch(emptyToNull(CsvImportUtil.getValue(record, "os_arch", "osArch")));
        entity.setOsRelease(emptyToNull(CsvImportUtil.getValue(record, "os_release", "osRelease")));
        entity.setCpuModel(emptyToNull(CsvImportUtil.getValue(record, "cpu_model", "cpuModel")));
        entity.setCpuPhysicalCores(parseInteger(CsvImportUtil.getValue(record, "cpu_physical_cores", "cpuPhysicalCores"), "cpu_physical_cores"));
        entity.setCpuLogicalCores(parseInteger(CsvImportUtil.getValue(record, "cpu_logical_cores", "cpuLogicalCores"), "cpu_logical_cores"));
        entity.setMemTotal(emptyToNull(CsvImportUtil.getValue(record, "mem_total", "memTotal")));
        entity.setMemUsed(emptyToNull(CsvImportUtil.getValue(record, "mem_used", "memUsed")));
        entity.setMemAvailable(emptyToNull(CsvImportUtil.getValue(record, "mem_available", "memAvailable")));
        entity.setMemUsage(emptyToNull(CsvImportUtil.getValue(record, "mem_usage", "memUsage")));
        entity.setStatus(parseStatus(CsvImportUtil.getValue(record, "status")));
        entity.setLastScanTime(parseDateTime(CsvImportUtil.getValue(record, "last_scan_time", "lastScanTime"), "last_scan_time"));
        return entity;
    }

    private void saveImportedRecord(HostEntity imported, CsvImportResultDTO result) {
        Long tenantId = currentTenantId();
        imported.setTenantId(tenantId);
        HostEntity existing = hostMapper.selectByNormalizedMacAndTenant(normalizeMac(imported.getMacAddress()), tenantId);
        if (existing == null) {
            licenseGuard.requireFeature(LicenseFeature.HOST);
            licenseGuard.requireHostQuotaBeforeCreate();
            if (imported.getStatus() == null) {
                imported.setStatus(1);
            }
            hostMapper.insert(imported);
            result.incrementInserted();
            return;
        }

        imported.setId(existing.getId());
        imported.setTenantId(tenantId);
        if (imported.getStatus() == null) {
            imported.setStatus(existing.getStatus());
        }
        if (imported.getLastScanTime() == null) {
            imported.setLastScanTime(existing.getLastScanTime());
        }
        hostMapper.updateById(imported);
        result.incrementUpdated();
    }

    private String normalizeMac(String mac) {
        if (mac == null) {
            return "";
        }
        return mac.toLowerCase().replaceAll("[^0-9a-f]", "");
    }

    @Override
    public void sendPortScan(PortScanDTO dto) {
        licenseGuard.requireFeature(LicenseFeature.ASSET);
        String macAddress = dto.getMacAddress();
        if (!StringUtils.hasText(macAddress)) {
            throw new IllegalArgumentException("MAC地址不能为空");
        }
        sendPortScanInternal(dto);
    }

    @Override
    public int autoPortScanOnlineHosts(int limit) {
        int batchSize = Math.max(1, limit);
        List<HostEntity> hosts = hostMapper.selectPortScanCandidates(batchSize);
        var strategy = probeStrategyService.getStrategyEntity();
        int sentCount = 0;
        for (HostEntity host : hosts) {
            if (host == null || !StringUtils.hasText(host.getMacAddress())) {
                continue;
            }
            PortScanDTO dto = new PortScanDTO();
            dto.setMacAddress(host.getMacAddress());
            dto.setScanRange(strategy != null && strategy.getPortScanRange() != null
                    ? strategy.getPortScanRange() : "common");
            dto.setCustomPorts(strategy != null ? strategy.getPortScanCustomPorts() : null);
            dto.setGrabBanner(strategy != null && strategy.getProbeFingerprint() != null
                    && strategy.getProbeFingerprint() == 1);
            try {
                sendPortScanInternal(dto);
                sentCount++;
            } catch (Exception e) {
                log.warn("自动端口扫描下发失败: hostId={}, mac={}, reason={}",
                        host.getId(), host.getMacAddress(), e.getMessage());
            }
        }
        return sentCount;
    }

    private void sendPortScanInternal(PortScanDTO dto) {
        String macAddress = dto.getMacAddress();
        String queueName = RabbitMQConfig.AGENT_QUEUE_PREFIX + normalizeMac(macAddress)
                + RabbitMQConfig.AGENT_QUEUE_SUFFIX;
        if (amqpAdmin.getQueueProperties(queueName) == null) {
            throw new IllegalArgumentException("客户端队列不存在，无法发送端口扫描指令");
        }

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", PROBE_TYPE_PORT_SCAN);
        message.put("macAddress", macAddress);
        message.put("scanRange", dto.getScanRange() != null ? dto.getScanRange() : "common");
        message.put("customPorts", dto.getCustomPorts() != null ? dto.getCustomPorts() : "");
        message.put("grabBanner", dto.isGrabBanner());

        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new IllegalStateException("组装端口扫描消息失败", e);
        }

        rabbitTemplate.convertAndSend(RabbitMQConfig.AGENT_EXCHANGE, macAddress, payload);
        log.info("端口扫描任务已下发: routingKey={}, payload={}", macAddress, payload);
    }

    private String requireField(CSVRecord record, String... headerNames) {
        String value = CsvImportUtil.getValue(record, headerNames);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("必填字段缺失: " + headerNames[0]);
        }
        return value.trim();
    }

    private Integer parseInteger(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " 必须是整数");
        }
    }

    private Integer parseStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Integer status = parseInteger(value, "status");
        if (status != 0 && status != 1) {
            throw new IllegalArgumentException("status 仅支持 0 或 1");
        }
        return status;
    }

    private LocalDateTime parseDateTime(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        for (DateTimeFormatter formatter : CSV_DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        throw new IllegalArgumentException(fieldName + " 时间格式不正确，支持 yyyy-MM-dd HH:mm:ss 或 ISO_LOCAL_DATE_TIME");
    }

    private HostEntity ensureExists(Long id) {
        HostEntity entity = hostMapper.selectByIdAndTenant(id, currentTenantId());
        if (entity == null) {
            throw new ResourceNotFoundException("记录不存在，id=" + id);
        }
        return entity;
    }

    private void validateMacUnique(Long id, String macAddress) {
        if (!StringUtils.hasText(macAddress)) {
            return;
        }
        HostEntity hostByMac = hostMapper.selectByMac(macAddress);
        if (hostByMac != null && !hostByMac.getId().equals(id)) {
            throw new IllegalArgumentException("MAC地址已存在");
        }
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private HostResponseDTO toResponse(HostEntity entity) {
        HostResponseDTO dto = new HostResponseDTO();
        dto.setId(entity.getId());
        dto.setHostname(entity.getHostname());
        dto.setIpv4(entity.getIpv4());
        dto.setMacAddress(entity.getMacAddress());
        dto.setOsName(entity.getOsName());
        dto.setOsVersion(entity.getOsVersion());
        dto.setOsArch(entity.getOsArch());
        dto.setOsRelease(entity.getOsRelease());
        dto.setCpuModel(entity.getCpuModel());
        dto.setCpuPhysicalCores(entity.getCpuPhysicalCores());
        dto.setCpuLogicalCores(entity.getCpuLogicalCores());
        dto.setMemTotal(entity.getMemTotal());
        dto.setMemUsed(entity.getMemUsed());
        dto.setMemAvailable(entity.getMemAvailable());
        dto.setMemUsage(entity.getMemUsage());
        dto.setStatus(entity.getStatus());
        dto.setLastScanTime(entity.getLastScanTime());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
