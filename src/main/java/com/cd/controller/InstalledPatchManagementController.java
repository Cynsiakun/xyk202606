package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.dto.InstalledPatchBatchDeleteDTO;
import com.cd.dto.InstalledPatchCreateDTO;
import com.cd.dto.InstalledPatchResponseDTO;
import com.cd.dto.InstalledPatchUpdateDTO;
import com.cd.service.InstalledPatchManagementService;
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
@RequestMapping("/api/installed-patch")
@RequiredArgsConstructor
public class InstalledPatchManagementController {

    private final InstalledPatchManagementService installedPatchManagementService;

    @PreAuthorize("@perm.has('installed-patch:create')")
    @PostMapping
    public Result<InstalledPatchResponseDTO> create(@Valid @RequestBody InstalledPatchCreateDTO dto) {
        return Result.success(installedPatchManagementService.create(dto));
    }

    @PreAuthorize("@perm.has('installed-patch:update')")
    @PutMapping("/{id}")
    public Result<InstalledPatchResponseDTO> update(@PathVariable @Min(value = 1, message = "id必须大于0") Long id,
                                                    @Valid @RequestBody InstalledPatchUpdateDTO dto) {
        return Result.success(installedPatchManagementService.update(id, dto));
    }

    @PreAuthorize("@perm.has('installed-patch:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(value = 1, message = "id必须大于0") Long id) {
        installedPatchManagementService.deleteById(id);
        return Result.success();
    }

    @PreAuthorize("@perm.has('installed-patch:delete')")
    @PostMapping("/batch-delete")
    public Result<Void> deleteBatch(@Valid @RequestBody InstalledPatchBatchDeleteDTO dto) {
        installedPatchManagementService.deleteBatch(dto.getIds());
        return Result.success();
    }

    @PreAuthorize("@perm.has('installed-patch:view')")
    @GetMapping("/{id}")
    public Result<InstalledPatchResponseDTO> getById(@PathVariable @Min(value = 1, message = "id必须大于0") Long id) {
        return Result.success(installedPatchManagementService.getById(id));
    }

    @PreAuthorize("@perm.has('installed-patch:view')")
    @GetMapping("/list")
    public Result<PageResult<InstalledPatchResponseDTO>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page必须大于0") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size必须大于0") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String installStatus) {
        return Result.success(installedPatchManagementService.list(page, size, keyword, installStatus));
    }
}
