package com.cd.service.impl;

import com.cd.entity.HostEntity;
import com.cd.entity.HostPatchStatusEntity;
import com.cd.entity.InstalledPatchEntity;
import com.cd.entity.MqErrorLogEntity;
import com.cd.mapper.HostMapper;
import com.cd.mapper.HostPatchStatusMapper;
import com.cd.mapper.InstalledPatchMapper;
import com.cd.mapper.MqErrorLogMapper;
import com.cd.service.PatchNormalizeService;
import com.cd.service.PatchScanService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatchScanServiceImpl implements PatchScanService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final HostMapper hostMapper;
    private final HostPatchStatusMapper hostPatchStatusMapper;
    private final InstalledPatchMapper installedPatchMapper;
    private final MqErrorLogMapper mqErrorLogMapper;
    private final PatchNormalizeService patchNormalizeService;

    @Override
    public void processPatchScanMessage(String queueName, String message) {
        try {
            log.info("收到补丁扫描消息: queue={}", queueName);

            JsonNode root;
            try {
                root = OBJECT_MAPPER.readTree(message);
            } catch (Exception e) {
                saveError(queueName, message, "JSON 格式非法: " + e.getMessage());
                return;
            }

            String type = text(root, "type");
            if (!"patch_scan".equals(type)) {
                saveError(queueName, message, "type 不匹配，期望 patch_scan，实际为: " + type);
                return;
            }

            JsonNode hostPatchNode = root.path("hostPatch");
            if (hostPatchNode.isMissingNode() || hostPatchNode.isNull() || !hostPatchNode.isObject()) {
                saveError(queueName, message, "缺少必要字段 hostPatch 或其类型不正确");
                return;
            }

            String rawMac = firstText(hostPatchNode, "macAddress", "mac");
            String normalizedMac = normalizeMac(rawMac);
            if (!StringUtils.hasText(normalizedMac)) {
                saveError(queueName, message, "缺少必要字段 hostPatch.macAddress/mac");
                return;
            }

            HostEntity host = hostMapper.selectByNormalizedMac(normalizedMac);
            if (host == null) {
                saveError(queueName, message, "未找到匹配主机, mac=" + rawMac);
                log.warn("补丁扫描消息未关联到主机: mac={}", rawMac);
                return;
            }

            Long hostId = host.getId();
            Long tenantId = host.getTenantId() == null ? 0L : host.getTenantId();
            JsonNode patchesNode = root.path("installedPatches");
            if (patchesNode.isMissingNode() || patchesNode.isNull() || !patchesNode.isArray()) {
                saveError(queueName, message, "缺少必要字段 installedPatches 或其类型不是数组");
                return;
            }

            log.info("关联主机成功: host_id={}, mac={}, patch_count={}", hostId, rawMac, patchesNode.size());

            LocalDateTime scanTime = parseDateTime(firstText(hostPatchNode, "scan_time", "scanTime"));
            upsertHostPatchStatus(hostId, tenantId, hostPatchNode, scanTime);
            log.info("host_patch_status入库成功: host_id={}", hostId);

            int count = 0;
            for (JsonNode patchNode : patchesNode) {
                upsertInstalledPatch(hostId, tenantId, patchNode, scanTime);
                count++;
            }
            updateHostLastScanTime(hostId, scanTime);
            log.info("installed_patch入库成功: host_id={}, patch_count={}", hostId, count);
        } catch (Exception e) {
            log.error("处理补丁扫描消息异常: queue={}", queueName, e);
            saveError(queueName, message, "服务端异常: " + e.getMessage());
        }
    }

    private void upsertHostPatchStatus(Long hostId, Long tenantId, JsonNode hostPatchNode, LocalDateTime scanTime) {
        HostPatchStatusEntity entity = hostPatchStatusMapper.selectLatestByHostIdAndTenant(hostId, tenantId);
        boolean create = entity == null;
        if (create) {
            entity = new HostPatchStatusEntity();
            entity.setTenantId(tenantId);
            entity.setHostId(hostId);
            entity.setCreatedAt(LocalDateTime.now());
        }

        entity.setTenantId(tenantId);
        entity.setOsFamily(firstText(hostPatchNode, "os_family", "osFamily"));
        entity.setOsBuild(firstText(hostPatchNode, "os_build", "osBuild"));
        entity.setKernelVersion(firstText(hostPatchNode, "kernel_version", "kernelVersion"));
        entity.setSupportStatus(firstText(hostPatchNode, "support_status", "supportStatus"));
        entity.setLastBootTime(parseDateTime(firstText(hostPatchNode, "last_boot_time", "lastBootTime")));
        entity.setPendingReboot(booleanToInt(booleanValue(hostPatchNode, "pending_reboot", "pendingReboot")));
        entity.setAssetCriticality(firstText(hostPatchNode, "asset_criticality", "assetCriticality"));
        entity.setRiskLevel(firstText(hostPatchNode, "risk_level", "riskLevel"));
        entity.setMissingPatchCount(integerValue(hostPatchNode, "missing_patch_count", "missingPatchCount", 0));
        entity.setScanTime(scanTime);
        entity.setUpdatedAt(LocalDateTime.now());

        if (create) {
            hostPatchStatusMapper.insert(entity);
        } else {
            hostPatchStatusMapper.updateById(entity);
        }
        hostPatchStatusMapper.deleteByHostIdAndTenantExcludeId(hostId, tenantId, entity.getId());
    }

    private void upsertInstalledPatch(Long hostId, Long tenantId, JsonNode patchNode, LocalDateTime scanTime) {
        String rawPatchId = firstText(patchNode, "patch_id", "patchId");
        String normalizedPatchId = patchNormalizeService.normalizePatchId(rawPatchId);
        if (!StringUtils.hasText(normalizedPatchId)) {
            log.warn("跳过无效补丁编号: host_id={}, raw_patch_id={}", hostId, rawPatchId);
            return;
        }

        InstalledPatchEntity entity = installedPatchMapper.selectLatestByHostIdAndPatchIdAndTenant(
                hostId, normalizedPatchId, tenantId);
        boolean create = entity == null;
        if (create) {
            entity = new InstalledPatchEntity();
            entity.setTenantId(tenantId);
            entity.setHostId(hostId);
        }

        entity.setTenantId(tenantId);
        entity.setPatchId(normalizedPatchId);
        entity.setPatchType(firstText(patchNode, "patch_type", "patchType"));
        entity.setProductName(firstText(patchNode, "product_name", "productName"));
        entity.setProductVersion(firstText(patchNode, "product_version", "productVersion"));
        entity.setInstallTime(parseDateTimeOrDate(firstText(patchNode, "install_time", "installTime")));
        entity.setInstallStatus(firstText(patchNode, "install_status", "installStatus"));
        entity.setSource(firstText(patchNode, "source"));
        entity.setSignatureStatus(firstText(patchNode, "signature_status", "signatureStatus"));
        entity.setRebootRequired(booleanToInt(booleanValue(patchNode, "reboot_required", "rebootRequired")));
        entity.setSupersededBy(firstText(patchNode, "superseded_by", "supersededBy"));
        entity.setIsSecurityPatch(booleanToInt(booleanValue(patchNode, "is_security_patch", "isSecurityPatch")));
        entity.setRawData(patchNode.toString());
        entity.setScanTime(scanTime);

        if (create) {
            installedPatchMapper.insert(entity);
        } else {
            installedPatchMapper.updateById(entity);
        }
        installedPatchMapper.deleteByHostIdAndPatchIdAndTenantExcludeId(
                hostId, normalizedPatchId, tenantId, entity.getId());
    }

    private void updateHostLastScanTime(Long hostId, LocalDateTime scanTime) {
        if (hostId != null) {
            hostMapper.updateLastScanTimeById(hostId, scanTime == null ? LocalDateTime.now() : scanTime);
        }
    }

    private String firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            String value = text(node, key);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node, String key) {
        JsonNode child = node.path(key);
        if (child.isMissingNode() || child.isNull()) {
            return null;
        }
        String value = child.asText();
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Integer integerValue(JsonNode node, String key1, String key2, int defaultValue) {
        String value = firstText(node, key1, key2);
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean booleanValue(JsonNode node, String key1, String key2) {
        JsonNode first = node.path(key1);
        if (!first.isMissingNode() && !first.isNull()) {
            return first.asBoolean();
        }
        JsonNode second = node.path(key2);
        if (!second.isMissingNode() && !second.isNull()) {
            return second.asBoolean();
        }
        return false;
    }

    private Integer booleanToInt(boolean value) {
        return value ? 1 : 0;
    }

    private LocalDateTime parseDateTimeOrDate(String value) {
        LocalDateTime dateTime = parseDateTime(value);
        if (dateTime != null) {
            return dateTime;
        }
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMATTER).atStartOfDay();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String normalizeMac(String mac) {
        if (!StringUtils.hasText(mac)) {
            return null;
        }
        String normalized = mac.toLowerCase().replaceAll("[^0-9a-f]", "");
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private void saveError(String queueName, String rawMessage, String errorReason) {
        try {
            MqErrorLogEntity errorLog = new MqErrorLogEntity();
            errorLog.setQueueName(queueName);
            errorLog.setRawMessage(rawMessage);
            errorLog.setErrorReason(errorReason);
            mqErrorLogMapper.insert(errorLog);
            log.warn("补丁扫描消息异常已记录: queue={}, reason={}", queueName, errorReason);
        } catch (Exception e) {
            log.error("写入 mq_error_logs 失败: queue={}, reason={}", queueName, errorReason, e);
        }
    }
}
