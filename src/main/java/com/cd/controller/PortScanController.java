package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.dto.PortScanDTO;
import com.cd.dto.PortScanRecordDTO;
import com.cd.entity.PortScanResultEntity;
import com.cd.mapper.PortScanResultMapper;
import com.cd.common.security.TenantContextHolder;
import com.cd.service.HostService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Validated
@RestController
@RequestMapping("/api/port-scan")
@RequiredArgsConstructor
public class PortScanController {

    private final PortScanResultMapper portScanResultMapper;
    private final HostService hostService;

    @PreAuthorize("@perm.has('port-scan:view')")
    @GetMapping("/list")
    public Result<PageResult<PortScanRecordDTO>> list(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String macAddress) {
        int offset = (page - 1) * size;
        Long tenantId = TenantContextHolder.getTenantId();
        List<PortScanResultEntity> items;
        long total;
        if (tenantId == null) {
            items = portScanResultMapper.selectPage(offset, size, keyword, macAddress);
            total = portScanResultMapper.countFiltered(keyword, macAddress);
        } else {
            items = portScanResultMapper.selectPageByTenant(offset, size, keyword, macAddress, tenantId);
            total = portScanResultMapper.countFilteredByTenant(keyword, macAddress, tenantId);
        }
        List<PortScanRecordDTO> dtos = items.stream().map(this::toRecord).collect(Collectors.toList());
        return Result.success(new PageResult<>(total, dtos));
    }

    @PreAuthorize("@perm.has('port-scan:view')")
    @GetMapping("/{id}")
    public Result<PortScanRecordDTO> detail(@PathVariable @Min(1) Long id) {
        Long tenantId = TenantContextHolder.getTenantId();
        PortScanResultEntity entity = tenantId == null
                ? portScanResultMapper.selectById(id)
                : portScanResultMapper.selectByIdAndTenant(id, tenantId);
        if (entity == null) {
            return Result.success(null);
        }
        PortScanRecordDTO dto = toRecord(entity);
        dto.setPortJson(entity.getPortJson());
        return Result.success(dto);
    }

    @PreAuthorize("@perm.has('port-scan:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(1) Long id) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            portScanResultMapper.softDeleteById(id);
        } else {
            portScanResultMapper.softDeleteByIdAndTenant(id, tenantId);
        }
        return Result.success();
    }

    @PreAuthorize("@perm.has('port-scan:trigger')")
    @PostMapping("/trigger")
    public Result<Void> trigger(@Valid @RequestBody PortScanDTO dto) {
        hostService.sendPortScan(dto);
        return Result.success();
    }

    @PreAuthorize("@perm.has('port-scan:view')")
    @GetMapping("/host-latest")
    public Result<PortScanRecordDTO> hostLatest(@RequestParam String mac) {
        Long tenantId = TenantContextHolder.getTenantId();
        PortScanResultEntity entity = tenantId == null
                ? portScanResultMapper.selectLatestByMac(mac)
                : portScanResultMapper.selectLatestByMacAndTenant(mac, tenantId);
        if (entity == null) {
            return Result.success(null);
        }
        return Result.success(toRecord(entity));
    }

    private PortScanRecordDTO toRecord(PortScanResultEntity entity) {
        PortScanRecordDTO dto = new PortScanRecordDTO();
        dto.setId(entity.getId());
        dto.setTaskId(entity.getTaskId());
        dto.setHostName(entity.getHostName());
        dto.setMacAddress(entity.getMacAddress());
        dto.setPortCount(entity.getPortCount());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
