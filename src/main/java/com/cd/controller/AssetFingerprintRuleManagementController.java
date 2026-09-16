package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.dto.AssetFingerprintRuleManageRequestDTO;
import com.cd.entity.AssetFingerprintRuleEntity;
import com.cd.service.AssetFingerprintRuleManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/asset-fingerprint-rules")
@RequiredArgsConstructor
@PreAuthorize("@perm.isSuperAdmin()")
public class AssetFingerprintRuleManagementController {

    private final AssetFingerprintRuleManagementService assetFingerprintRuleManagementService;

    @PreAuthorize("@perm.has('asset-fingerprint-rule:view')")
    @GetMapping
    public Result<PageResult<AssetFingerprintRuleEntity>> list(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer port,
            @RequestParam(required = false) Integer enabled) {
        return Result.success(assetFingerprintRuleManagementService.list(page, size, keyword, category, port, enabled));
    }

    @PreAuthorize("@perm.has('asset-fingerprint-rule:view')")
    @GetMapping("/{id}")
    public Result<AssetFingerprintRuleEntity> detail(@PathVariable @Min(1) Long id) {
        return Result.success(assetFingerprintRuleManagementService.detail(id));
    }

    @PreAuthorize("@perm.has('asset-fingerprint-rule:create')")
    @PostMapping
    public Result<AssetFingerprintRuleEntity> create(@Valid @RequestBody AssetFingerprintRuleManageRequestDTO request) {
        return Result.success(assetFingerprintRuleManagementService.create(request));
    }

    @PreAuthorize("@perm.has('asset-fingerprint-rule:update')")
    @PutMapping("/{id}")
    public Result<AssetFingerprintRuleEntity> update(@PathVariable @Min(1) Long id,
                                                     @Valid @RequestBody AssetFingerprintRuleManageRequestDTO request) {
        return Result.success(assetFingerprintRuleManagementService.update(id, request));
    }

    @PreAuthorize("@perm.has('asset-fingerprint-rule:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(1) Long id) {
        assetFingerprintRuleManagementService.delete(id);
        return Result.success();
    }
}
