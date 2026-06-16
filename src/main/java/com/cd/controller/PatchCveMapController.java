package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.dto.CsvImportResultDTO;
import com.cd.dto.PatchCveMapBatchDeleteDTO;
import com.cd.dto.PatchCveMapCreateDTO;
import com.cd.dto.PatchCveMapResponseDTO;
import com.cd.dto.PatchCveMapUpdateDTO;
import com.cd.service.PatchCveMapService;
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
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/patch-cve-map")
@RequiredArgsConstructor
@PreAuthorize("@licenseGuard.hasFeature('PATCH')")
public class PatchCveMapController {

    private final PatchCveMapService patchCveMapService;

    @PreAuthorize("@perm.has('patch-cve-map:create')")
    @PostMapping
    public Result<PatchCveMapResponseDTO> create(@Valid @RequestBody PatchCveMapCreateDTO dto) {
        return Result.success(patchCveMapService.create(dto));
    }

    @PreAuthorize("@perm.has('patch-cve-map:update')")
    @PutMapping("/{id}")
    public Result<PatchCveMapResponseDTO> update(
            @PathVariable @Min(value = 1, message = "id必须大于0") Long id,
            @Valid @RequestBody PatchCveMapUpdateDTO dto) {
        return Result.success(patchCveMapService.update(id, dto));
    }

    @PreAuthorize("@perm.has('patch-cve-map:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(value = 1, message = "id必须大于0") Long id) {
        patchCveMapService.deleteById(id);
        return Result.success();
    }

    @PreAuthorize("@perm.has('patch-cve-map:delete')")
    @PostMapping("/batch-delete")
    public Result<Void> deleteBatch(@Valid @RequestBody PatchCveMapBatchDeleteDTO dto) {
        patchCveMapService.deleteBatch(dto.getIds());
        return Result.success();
    }

    @PreAuthorize("@perm.has('patch-cve-map:view')")
    @GetMapping("/{id}")
    public Result<PatchCveMapResponseDTO> getById(
            @PathVariable @Min(value = 1, message = "id必须大于0") Long id) {
        return Result.success(patchCveMapService.getById(id));
    }

    @PreAuthorize("@perm.has('patch-cve-map:view')")
    @GetMapping("/list")
    public Result<PageResult<PatchCveMapResponseDTO>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page必须大于0") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size必须大于0") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) Integer kevFlag) {
        return Result.success(patchCveMapService.list(page, size, keyword, severity, kevFlag));
    }

    @PreAuthorize("@perm.has('patch-cve-map:create')")
    @PostMapping("/import")
    public Result<CsvImportResultDTO> importCsv(@RequestParam("file") MultipartFile file) {
        return Result.success(patchCveMapService.importCsv(file));
    }
}
