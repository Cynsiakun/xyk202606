package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.dto.AssetOverviewDTO;
import com.cd.dto.AssetRecordDTO;
import com.cd.entity.AccountEntity;
import com.cd.entity.AppEntity;
import com.cd.entity.ProcessEntity;
import com.cd.entity.ServiceEntity;
import com.cd.mapper.AccountMapper;
import com.cd.mapper.AppMapper;
import com.cd.mapper.ProcessMapper;
import com.cd.mapper.ServiceMapper;
import com.cd.service.AssetQueryService;
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

/**
 * 资产查询实现。
 *
 * <p>总览按 host 维度聚合四类资产的最新计数，列表/详情/删除均走对应 mapper。
 * hostScope 参数当前预留，不参与实际过滤。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetQueryServiceImpl implements AssetQueryService {

    private final AccountMapper accountMapper;
    private final ServiceMapper serviceMapper;
    private final ProcessMapper processMapper;
    private final AppMapper appMapper;

    // ——— 总览 ———

    @Override
    public PageResult<AssetOverviewDTO> overview(int page, int size, String keyword, String hostScope) {
        // 从四张表取每个 MAC 的最新记录
        Map<String, AssetOverviewDTO> map = new LinkedHashMap<>();

        mergeIntoOverview(map, accountMapper.selectLatestPerMac(), "account");
        mergeIntoOverview(map, serviceMapper.selectLatestPerMac(), "service");
        mergeIntoOverview(map, processMapper.selectLatestPerMac(), "process");
        mergeIntoOverview(map, appMapper.selectLatestPerMac(), "app");

        List<AssetOverviewDTO> all = new ArrayList<>(map.values());
        // 关键字过滤
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim().toLowerCase();
            all.removeIf(dto -> !dto.getHostName().toLowerCase().contains(kw)
                    && !dto.getMacAddress().toLowerCase().contains(kw));
        }

        all.sort(Comparator.comparing(AssetOverviewDTO::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        long total = all.size();
        int offset = (page - 1) * size;
        int end = Math.min(offset + size, all.size());
        List<AssetOverviewDTO> pageList = offset < all.size() ? all.subList(offset, end) : List.of();
        return new PageResult<>(total, pageList);
    }

    private void mergeIntoOverview(Map<String, AssetOverviewDTO> map, List<?> entities, String type) {
        for (Object obj : entities) {            String mac;
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
            if (!StringUtils.hasText(mac)) continue;

            AssetOverviewDTO dto = map.computeIfAbsent(mac, k -> {
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
            }
            if (updatedAt != null && (dto.getUpdatedAt() == null || updatedAt.isAfter(dto.getUpdatedAt()))) {
                dto.setUpdatedAt(updatedAt);
            }
        }
    }

    // ——— 按 MAC 取最新记录（主机维度资产弹窗） ———

    @Override
    public AssetRecordDTO latestByMac(String assetType, String macAddress) {
        if (!StringUtils.hasText(macAddress) || !StringUtils.hasText(assetType)) {
            return null;
        }
        String mac = macAddress.trim();
        return switch (assetType) {
            case "account" -> accountMapper.selectPage(0, 1, null, mac).stream()
                    .findFirst().map(this::toAccountDTO).orElse(null);
            case "service" -> serviceMapper.selectPage(0, 1, null, mac).stream()
                    .findFirst().map(this::toServiceDTO).orElse(null);
            case "process" -> processMapper.selectPage(0, 1, null, mac).stream()
                    .findFirst().map(this::toProcessDTO).orElse(null);
            case "app" -> appMapper.selectPage(0, 1, null, mac).stream()
                    .findFirst().map(this::toAppDTO).orElse(null);
            default -> null;
        };
    }

    // ——— 账号资产 ———

    @Override
    public PageResult<AssetRecordDTO> accountList(int page, int size, String keyword, String hostScope) {
        int offset = (page - 1) * size;
        List<AccountEntity> entities = accountMapper.selectPage(offset, size, emptyToNull(keyword), emptyToNull(hostScope));
        long total = accountMapper.countFiltered(emptyToNull(keyword), emptyToNull(hostScope));
        return new PageResult<>(total, entities.stream().map(this::toAccountDTO).toList());
    }

    @Override
    public AssetRecordDTO accountDetail(Long id) {
        AccountEntity entity = accountMapper.selectById(id);
        if (entity == null) throw new ResourceNotFoundException("账号资产记录不存在: id=" + id);
        return toAccountDTO(entity);
    }

    @Override
    public void deleteAccount(Long id) {
        AccountEntity entity = accountMapper.selectById(id);
        if (entity == null) throw new ResourceNotFoundException("账号资产记录不存在: id=" + id);
        accountMapper.softDeleteById(id);
    }

    // ——— 服务资产 ———

    @Override
    public PageResult<AssetRecordDTO> serviceList(int page, int size, String keyword, String hostScope) {
        int offset = (page - 1) * size;
        List<ServiceEntity> entities = serviceMapper.selectPage(offset, size, emptyToNull(keyword), emptyToNull(hostScope));
        long total = serviceMapper.countFiltered(emptyToNull(keyword), emptyToNull(hostScope));
        return new PageResult<>(total, entities.stream().map(this::toServiceDTO).toList());
    }

    @Override
    public AssetRecordDTO serviceDetail(Long id) {
        ServiceEntity entity = serviceMapper.selectById(id);
        if (entity == null) throw new ResourceNotFoundException("服务资产记录不存在: id=" + id);
        return toServiceDTO(entity);
    }

    @Override
    public void deleteService(Long id) {
        ServiceEntity entity = serviceMapper.selectById(id);
        if (entity == null) throw new ResourceNotFoundException("服务资产记录不存在: id=" + id);
        serviceMapper.softDeleteById(id);
    }

    // ——— 进程资产 ———

    @Override
    public PageResult<AssetRecordDTO> processList(int page, int size, String keyword, String hostScope) {
        int offset = (page - 1) * size;
        List<ProcessEntity> entities = processMapper.selectPage(offset, size, emptyToNull(keyword), emptyToNull(hostScope));
        long total = processMapper.countFiltered(emptyToNull(keyword), emptyToNull(hostScope));
        return new PageResult<>(total, entities.stream().map(this::toProcessDTO).toList());
    }

    @Override
    public AssetRecordDTO processDetail(Long id) {
        ProcessEntity entity = processMapper.selectById(id);
        if (entity == null) throw new ResourceNotFoundException("进程资产记录不存在: id=" + id);
        return toProcessDTO(entity);
    }

    @Override
    public void deleteProcess(Long id) {
        ProcessEntity entity = processMapper.selectById(id);
        if (entity == null) throw new ResourceNotFoundException("进程资产记录不存在: id=" + id);
        processMapper.softDeleteById(id);
    }

    // ——— APP资产 ———

    @Override
    public PageResult<AssetRecordDTO> appList(int page, int size, String keyword, String hostScope) {
        int offset = (page - 1) * size;
        List<AppEntity> entities = appMapper.selectPage(offset, size, emptyToNull(keyword), emptyToNull(hostScope));
        long total = appMapper.countFiltered(emptyToNull(keyword), emptyToNull(hostScope));
        return new PageResult<>(total, entities.stream().map(this::toAppDTO).toList());
    }

    @Override
    public AssetRecordDTO appDetail(Long id) {
        AppEntity entity = appMapper.selectById(id);
        if (entity == null) throw new ResourceNotFoundException("APP资产记录不存在: id=" + id);
        return toAppDTO(entity);
    }

    @Override
    public void deleteApp(Long id) {
        AppEntity entity = appMapper.selectById(id);
        if (entity == null) throw new ResourceNotFoundException("APP资产记录不存在: id=" + id);
        appMapper.softDeleteById(id);
    }

    // ——— 转换 ———

    private AssetRecordDTO toAccountDTO(AccountEntity e) {
        AssetRecordDTO dto = new AssetRecordDTO();
        dto.setId(e.getId());
        dto.setTaskId(e.getTaskId());
        dto.setHostName(e.getHostName());
        dto.setMacAddress(e.getMacAddress());
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
        dto.setAssetCount(e.getAssetCount());
        dto.setAssetJson(e.getAssetJson());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());
        return dto;
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
