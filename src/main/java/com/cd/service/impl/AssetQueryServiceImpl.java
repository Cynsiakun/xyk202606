package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.common.security.TenantContextHolder;
import com.cd.dto.AssetOverviewDTO;
import com.cd.dto.AssetRecordDTO;
import com.cd.entity.AccountEntity;
import com.cd.entity.AppEntity;
import com.cd.entity.HostAssetInventoryEntity;
import com.cd.entity.HostEntity;
import com.cd.entity.ProcessEntity;
import com.cd.entity.ServiceEntity;
import com.cd.mapper.AccountMapper;
import com.cd.mapper.AppMapper;
import com.cd.mapper.HostAssetInventoryMapper;
import com.cd.mapper.HostMapper;
import com.cd.mapper.ProcessMapper;
import com.cd.mapper.ServiceMapper;
import com.cd.service.AssetQueryService;
import com.cd.service.PortFingerprintService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetQueryServiceImpl implements AssetQueryService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SOURCE_PLATFORM = "PLATFORM";

    private final AccountMapper accountMapper;
    private final ServiceMapper serviceMapper;
    private final ProcessMapper processMapper;
    private final AppMapper appMapper;
    private final HostMapper hostMapper;
    private final HostAssetInventoryMapper hostAssetInventoryMapper;
    private final PortFingerprintService portFingerprintService;

    @Override
    public PageResult<AssetOverviewDTO> overview(int page, int size, String keyword, String hostScope) {
        Map<String, AssetOverviewDTO> map = new LinkedHashMap<>();

        Long tenantId = currentTenantId();
        mergeIntoOverview(map, accountMapper.selectLatestPerMacByTenant(tenantId), "account");
        mergeIntoOverview(map, serviceMapper.selectLatestPerMacByTenant(tenantId), "service");
        mergeIntoOverview(map, processMapper.selectLatestPerMacByTenant(tenantId), "process");
        mergeIntoOverview(map, appMapper.selectLatestPerMacByTenant(tenantId), "app");

        List<AssetOverviewDTO> all = new ArrayList<>(map.values());
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim().toLowerCase();
            all.removeIf(dto -> !safeLower(dto.getHostName()).contains(kw)
                    && !safeLower(dto.getMacAddress()).contains(kw));
        }

        all.sort(Comparator.comparing(AssetOverviewDTO::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        long total = all.size();
        int offset = (page - 1) * size;
        int end = Math.min(offset + size, all.size());
        List<AssetOverviewDTO> pageList = offset < all.size() ? all.subList(offset, end) : List.of();
        return new PageResult<>(total, pageList);
    }

    private void mergeIntoOverview(Map<String, AssetOverviewDTO> map, List<?> entities, String type) {
        for (Object obj : entities) {
            String mac;
            String hostName;
            Integer count;
            LocalDateTime updatedAt;
            if (obj instanceof AccountEntity e) {
                mac = e.getMacAddress();
                hostName = e.getHostName();
                count = e.getAssetCount();
                updatedAt = e.getUpdatedAt();
            } else if (obj instanceof ServiceEntity e) {
                mac = e.getMacAddress();
                hostName = e.getHostName();
                count = e.getAssetCount();
                updatedAt = e.getUpdatedAt();
            } else if (obj instanceof ProcessEntity e) {
                mac = e.getMacAddress();
                hostName = e.getHostName();
                count = e.getAssetCount();
                updatedAt = e.getUpdatedAt();
            } else if (obj instanceof AppEntity e) {
                mac = e.getMacAddress();
                hostName = e.getHostName();
                count = e.getAssetCount();
                updatedAt = e.getUpdatedAt();
            } else {
                continue;
            }
            if (!StringUtils.hasText(mac)) {
                continue;
            }

            AssetOverviewDTO dto = map.computeIfAbsent(mac, key -> {
                AssetOverviewDTO n = new AssetOverviewDTO();
                n.setHostName(hostName);
                n.setMacAddress(mac);
                return n;
            });
            switch (type) {
                case "account" -> dto.setAccountCount(count);
                case "service" -> dto.setServiceCount(count);
                case "process" -> dto.setProcessCount(count);
                case "app" -> dto.setAppCount(count);
                default -> {
                }
            }
            if (updatedAt != null && (dto.getUpdatedAt() == null || updatedAt.isAfter(dto.getUpdatedAt()))) {
                dto.setUpdatedAt(updatedAt);
            }
        }
    }

    @Override
    public AssetRecordDTO latestByMac(String assetType, String macAddress) {
        if (!StringUtils.hasText(macAddress) || !StringUtils.hasText(assetType)) {
            return null;
        }
        String mac = macAddress.trim();
        Long tenantId = currentTenantId();
        return switch (assetType) {
            case "account" -> accountMapper.selectPageByTenant(0, 1, null, mac, tenantId).stream()
                    .findFirst().map(this::toAccountDTO).orElse(null);
            case "service" -> serviceMapper.selectPageByTenant(0, 1, null, mac, tenantId).stream()
                    .findFirst().map(this::toServiceDTO).orElse(null);
            case "process" -> processMapper.selectPageByTenant(0, 1, null, mac, tenantId).stream()
                    .findFirst().map(this::toProcessDTO).orElse(null);
            case "app" -> appMapper.selectPageByTenant(0, 1, null, mac, tenantId).stream()
                    .findFirst().map(this::toAppDTO).orElse(null);
            case "port" -> {
                HostAssetInventoryEntity entity = hostAssetInventoryMapper.selectLatestByMacAndTenant(mac, tenantId);
                yield entity == null ? null : buildPortTaskDTO(entity.getHostId(), entity.getTaskId(), tenantId);
            }
            default -> null;
        };
    }

    @Override
    public PageResult<AssetRecordDTO> accountList(int page, int size, String keyword, String hostScope) {
        int offset = (page - 1) * size;
        Long tenantId = currentTenantId();
        List<AccountEntity> entities = accountMapper.selectPageByTenant(offset, size, emptyToNull(keyword), emptyToNull(hostScope), tenantId);
        long total = accountMapper.countFilteredByTenant(emptyToNull(keyword), emptyToNull(hostScope), tenantId);
        return new PageResult<>(total, entities.stream().map(this::toAccountDTO).toList());
    }

    @Override
    public AssetRecordDTO accountDetail(Long id) {
        AccountEntity entity = accountMapper.selectByIdAndTenant(id, currentTenantId());
        if (entity == null) {
            throw new ResourceNotFoundException("账号资产记录不存在 id=" + id);
        }
        return toAccountDTO(entity);
    }

    @Override
    public void deleteAccount(Long id) {
        Long tenantId = currentTenantId();
        AccountEntity entity = accountMapper.selectByIdAndTenant(id, tenantId);
        if (entity == null) {
            throw new ResourceNotFoundException("账号资产记录不存在 id=" + id);
        }
        accountMapper.softDeleteByIdAndTenant(id, tenantId);
    }

    @Override
    public PageResult<AssetRecordDTO> serviceList(int page, int size, String keyword, String hostScope) {
        int offset = (page - 1) * size;
        Long tenantId = currentTenantId();
        List<ServiceEntity> entities = serviceMapper.selectPageByTenant(offset, size, emptyToNull(keyword), emptyToNull(hostScope), tenantId);
        long total = serviceMapper.countFilteredByTenant(emptyToNull(keyword), emptyToNull(hostScope), tenantId);
        return new PageResult<>(total, entities.stream().map(this::toServiceDTO).toList());
    }

    @Override
    public AssetRecordDTO serviceDetail(Long id) {
        ServiceEntity entity = serviceMapper.selectByIdAndTenant(id, currentTenantId());
        if (entity == null) {
            throw new ResourceNotFoundException("服务资产记录不存在 id=" + id);
        }
        return toServiceDTO(entity);
    }

    @Override
    public void deleteService(Long id) {
        Long tenantId = currentTenantId();
        ServiceEntity entity = serviceMapper.selectByIdAndTenant(id, tenantId);
        if (entity == null) {
            throw new ResourceNotFoundException("服务资产记录不存在 id=" + id);
        }
        serviceMapper.softDeleteByIdAndTenant(id, tenantId);
    }

    @Override
    public PageResult<AssetRecordDTO> processList(int page, int size, String keyword, String hostScope) {
        int offset = (page - 1) * size;
        Long tenantId = currentTenantId();
        List<ProcessEntity> entities = processMapper.selectPageByTenant(offset, size, emptyToNull(keyword), emptyToNull(hostScope), tenantId);
        long total = processMapper.countFilteredByTenant(emptyToNull(keyword), emptyToNull(hostScope), tenantId);
        return new PageResult<>(total, entities.stream().map(this::toProcessDTO).toList());
    }

    @Override
    public AssetRecordDTO processDetail(Long id) {
        ProcessEntity entity = processMapper.selectByIdAndTenant(id, currentTenantId());
        if (entity == null) {
            throw new ResourceNotFoundException("进程资产记录不存在 id=" + id);
        }
        return toProcessDTO(entity);
    }

    @Override
    public void deleteProcess(Long id) {
        Long tenantId = currentTenantId();
        ProcessEntity entity = processMapper.selectByIdAndTenant(id, tenantId);
        if (entity == null) {
            throw new ResourceNotFoundException("进程资产记录不存在 id=" + id);
        }
        processMapper.softDeleteByIdAndTenant(id, tenantId);
    }

    @Override
    public PageResult<AssetRecordDTO> appList(int page, int size, String keyword, String hostScope) {
        int offset = (page - 1) * size;
        Long tenantId = currentTenantId();
        List<AppEntity> entities = appMapper.selectPageByTenant(offset, size, emptyToNull(keyword), emptyToNull(hostScope), tenantId);
        long total = appMapper.countFilteredByTenant(emptyToNull(keyword), emptyToNull(hostScope), tenantId);
        return new PageResult<>(total, entities.stream().map(this::toAppDTO).toList());
    }

    @Override
    public AssetRecordDTO appDetail(Long id) {
        AppEntity entity = appMapper.selectByIdAndTenant(id, currentTenantId());
        if (entity == null) {
            throw new ResourceNotFoundException("APP资产记录不存在 id=" + id);
        }
        return toAppDTO(entity);
    }

    @Override
    public void deleteApp(Long id) {
        Long tenantId = currentTenantId();
        AppEntity entity = appMapper.selectByIdAndTenant(id, tenantId);
        if (entity == null) {
            throw new ResourceNotFoundException("APP资产记录不存在 id=" + id);
        }
        appMapper.softDeleteByIdAndTenant(id, tenantId);
    }

    @Override
    public PageResult<AssetRecordDTO> portList(int page, int size, String keyword, String hostScope) {
        int offset = (page - 1) * size;
        Long tenantId = currentTenantId();
        List<HostAssetInventoryEntity> entities = hostAssetInventoryMapper.selectTaskPageByTenant(
                offset, size, emptyToNull(keyword), emptyToNull(hostScope), tenantId);
        long total = hostAssetInventoryMapper.countTaskFilteredByTenant(emptyToNull(keyword), emptyToNull(hostScope), tenantId);
        return new PageResult<>(total, entities.stream()
                .map(entity -> buildPortTaskDTO(entity.getHostId(), entity.getTaskId(), tenantId))
                .filter(Objects::nonNull)
                .toList());
    }

    @Override
    public AssetRecordDTO portDetail(Long id) {
        HostAssetInventoryEntity entity = hostAssetInventoryMapper.selectByIdAndTenant(id, currentTenantId());
        if (entity == null) {
            throw new ResourceNotFoundException("端口资产记录不存在 id=" + id);
        }
        return buildPortTaskDTO(entity.getHostId(), entity.getTaskId(), currentTenantId());
    }

    @Override
    public void deletePort(Long id) {
        Long tenantId = currentTenantId();
        HostAssetInventoryEntity entity = hostAssetInventoryMapper.selectByIdAndTenant(id, tenantId);
        if (entity == null) {
            throw new ResourceNotFoundException("端口资产记录不存在 id=" + id);
        }
        hostAssetInventoryMapper.deleteByIdAndTenant(id, tenantId);
    }

    @Override
    public int rematchPort(Long id) {
        return portFingerprintService.rematchByTask(id, currentTenantId());
    }

    @Override
    public int rematchLatestPortByMac(String macAddress) {
        return portFingerprintService.rematchLatestByMac(macAddress, currentTenantId());
    }

    private AssetRecordDTO toAccountDTO(AccountEntity e) {
        AssetRecordDTO dto = new AssetRecordDTO();
        dto.setId(e.getId());
        dto.setTaskId(e.getTaskId());
        dto.setHostName(e.getHostName());
        dto.setMacAddress(e.getMacAddress());
        dto.setSource(defaultSource(e.getSource()));
        dto.setAssetCount(e.getAssetCount());
        dto.setAssetJson(e.getAssetJson());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());
        return dto;
    }

    private AssetRecordDTO toServiceDTO(ServiceEntity e) {
        AssetRecordDTO dto = new AssetRecordDTO();
        dto.setId(e.getId());
        dto.setTaskId(e.getTaskId());
        dto.setHostName(e.getHostName());
        dto.setMacAddress(e.getMacAddress());
        dto.setSource(defaultSource(e.getSource()));
        dto.setAssetCount(e.getAssetCount());
        dto.setAssetJson(e.getAssetJson());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());
        return dto;
    }

    private AssetRecordDTO toProcessDTO(ProcessEntity e) {
        AssetRecordDTO dto = new AssetRecordDTO();
        dto.setId(e.getId());
        dto.setTaskId(e.getTaskId());
        dto.setHostName(e.getHostName());
        dto.setMacAddress(e.getMacAddress());
        dto.setSource(defaultSource(e.getSource()));
        dto.setAssetCount(e.getAssetCount());
        dto.setAssetJson(e.getAssetJson());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());
        return dto;
    }

    private AssetRecordDTO toAppDTO(AppEntity e) {
        AssetRecordDTO dto = new AssetRecordDTO();
        dto.setId(e.getId());
        dto.setTaskId(e.getTaskId());
        dto.setHostName(e.getHostName());
        dto.setMacAddress(e.getMacAddress());
        dto.setSource(defaultSource(e.getSource()));
        dto.setAssetCount(e.getAssetCount());
        dto.setAssetJson(e.getAssetJson());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());
        return dto;
    }

    private AssetRecordDTO buildPortTaskDTO(Long hostId, String taskId, Long tenantId) {
        if (hostId == null || !StringUtils.hasText(taskId)) {
            return null;
        }
        List<HostAssetInventoryEntity> entities = hostAssetInventoryMapper.selectByHostIdAndTaskIdAndTenant(hostId, taskId, tenantId);
        if (entities == null || entities.isEmpty()) {
            return null;
        }
        HostAssetInventoryEntity first = entities.get(0);
        HostEntity host = hostMapper.selectByIdAndTenant(hostId, tenantId);

        AssetRecordDTO dto = new AssetRecordDTO();
        dto.setId(first.getId());
        dto.setTaskId(taskId);
        dto.setHostName(host == null ? null : host.getHostname());
        dto.setMacAddress(host == null ? first.getMacAddress() : host.getMacAddress());
        dto.setSource(defaultSource(first.getSource()));
        dto.setProductName("端口资产");
        dto.setProductType("port-task");
        dto.setPortCount(entities.size());
        dto.setAssetCount(entities.size());
        dto.setAssetJson(buildPortTaskAssetJson(entities));
        dto.setCreatedAt(entities.stream()
                .map(HostAssetInventoryEntity::getCreatedAt)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(first.getCreatedAt()));
        dto.setUpdatedAt(entities.stream()
                .map(HostAssetInventoryEntity::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(first.getUpdatedAt()));
        return dto;
    }

    private String buildPortTaskAssetJson(List<HostAssetInventoryEntity> items) {
        ArrayNode arrayNode = OBJECT_MAPPER.createArrayNode();
        for (HostAssetInventoryEntity item : items) {
            arrayNode.add(buildPortAssetItemNode(item));
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(arrayNode);
        } catch (Exception e) {
            log.error("构建端口资产任务JSON失败: hostId={}, taskId={}, count={}",
                    items.isEmpty() ? null : items.get(0).getHostId(),
                    items.isEmpty() ? null : items.get(0).getTaskId(),
                    items.size(),
                    e);
            return "[]";
        }
    }

    private ObjectNode buildPortAssetItemNode(HostAssetInventoryEntity e) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        if (e.getPort() == null) {
            node.putNull("port");
        } else {
            node.put("port", e.getPort());
        }
        node.put("productName", nullToEmpty(e.getProductName()));
        node.put("productType", nullToEmpty(e.getCategory()));
        node.put("subCategory", nullToEmpty(e.getSubCategory()));
        node.put("vendor", nullToEmpty(e.getVendor()));
        node.put("protocol", nullToEmpty(e.getProtocol()));
        node.put("confidence", e.getConfidence() == null ? 0 : e.getConfidence());
        if (e.getRuleId() == null) {
            node.putNull("ruleId");
        } else {
            node.put("ruleId", e.getRuleId());
        }
        node.put("banner", sanitizeText(e.getBannerRaw()));
        return node;
    }

    private String sanitizeText(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isISOControl(ch) && ch != '\r' && ch != '\n' && ch != '\t') {
                builder.append(' ');
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private String defaultSource(String source) {
        return StringUtils.hasText(source) ? source : SOURCE_PLATFORM;
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }
}
