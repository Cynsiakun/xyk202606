package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.dto.BaselineRuleManageRequestDTO;
import com.cd.entity.BaselineRuleEntity;
import com.cd.service.BaselineRuleManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/baseline/rule-management")
@RequiredArgsConstructor
@PreAuthorize("@perm.isSuperAdmin()")
public class BaselineRuleManagementController {

    private final BaselineRuleManagementService baselineRuleManagementService;

    @PreAuthorize("@perm.has('baseline-rule:view')")
    @GetMapping
    public Result<PageResult<BaselineRuleEntity>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page必须大于0") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size必须大于0") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer enabled) {
        return Result.success(baselineRuleManagementService.list(page, size, keyword, category, severity, status, enabled));
    }

    @PreAuthorize("@perm.has('baseline-rule:view')")
    @GetMapping("/{id}")
    public Result<BaselineRuleEntity> detail(@PathVariable @Min(value = 1, message = "id必须大于0") Long id) {
        return Result.success(baselineRuleManagementService.detail(id));
    }

    @PreAuthorize("@perm.has('baseline-rule:create')")
    @PostMapping
    public Result<BaselineRuleEntity> create(@Valid @RequestBody BaselineRuleManageRequestDTO request) {
        return Result.success(baselineRuleManagementService.create(request));
    }

    @PreAuthorize("@perm.has('baseline-rule:update')")
    @PutMapping("/{id}")
    public Result<BaselineRuleEntity> update(@PathVariable @Min(value = 1, message = "id必须大于0") Long id,
                                             @Valid @RequestBody BaselineRuleManageRequestDTO request) {
        return Result.success(baselineRuleManagementService.update(id, request));
    }

    @PreAuthorize("@perm.has('baseline-rule:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> archive(@PathVariable @Min(value = 1, message = "id必须大于0") Long id) {
        baselineRuleManagementService.archive(id);
        return Result.success();
    }

    @PreAuthorize("@perm.has('baseline-rule:update')")
    @PatchMapping("/{id}/enabled")
    public Result<BaselineRuleEntity> setEnabled(@PathVariable @Min(value = 1, message = "id必须大于0") Long id,
                                                 @RequestParam Integer enabled) {
        return Result.success(baselineRuleManagementService.setEnabled(id, enabled));
    }
}
