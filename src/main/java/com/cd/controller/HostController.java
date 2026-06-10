package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.dto.AssetProbeDTO;
import com.cd.dto.CsvImportResultDTO;
import com.cd.dto.HostCreateDTO;
import com.cd.dto.HostResponseDTO;
import com.cd.dto.HostUpdateDTO;
import com.cd.dto.ProbeStrategyDTO;
import com.cd.service.HostService;
import com.cd.service.ProbeStrategyService;
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
@RequestMapping("/api/host")
@RequiredArgsConstructor
public class HostController {

    private final HostService hostService;
    private final ProbeStrategyService probeStrategyService;

    @PreAuthorize("@perm.has('host:create')")
    @PostMapping
    public Result<HostResponseDTO> create(@Valid @RequestBody HostCreateDTO dto) {
        return Result.success(hostService.create(dto));
    }

    @PreAuthorize("@perm.has('host:update')")
    @PutMapping("/{id}")
    public Result<HostResponseDTO> update(@PathVariable @Min(value = 1, message = "id必须大于0") Long id,
                                          @Valid @RequestBody HostUpdateDTO dto) {
        return Result.success(hostService.update(id, dto));
    }

    @PreAuthorize("@perm.has('host:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(value = 1, message = "id必须大于0") Long id) {
        hostService.deleteById(id);
        return Result.success();
    }

    @PreAuthorize("@perm.has('host:view')")
    @GetMapping("/{id}")
    public Result<HostResponseDTO> getById(@PathVariable @Min(value = 1, message = "id必须大于0") Long id) {
        return Result.success(hostService.getById(id));
    }

    @PreAuthorize("@perm.has('host:view')")
    @GetMapping("/list")
    public Result<PageResult<HostResponseDTO>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page必须大于0") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size必须大于0") Integer size,
            @RequestParam(required = false) String keyword) {
        return Result.success(hostService.list(page, size, keyword));
    }

    @PreAuthorize("@perm.has('host:create')")
    @PostMapping("/import")
    public Result<CsvImportResultDTO> importCsv(@RequestParam("file") MultipartFile file) {
        return Result.success(hostService.importCsv(file));
    }

    @PreAuthorize("@perm.has('host:probe')")
    @PostMapping("/probe")
    public Result<Void> probe(@Valid @RequestBody AssetProbeDTO dto) {
        hostService.sendAssetProbe(dto);
        return Result.success();
    }

    @PreAuthorize("@perm.has('host:probe')")
    @GetMapping("/probe-strategy")
    public Result<ProbeStrategyDTO> getProbeStrategy() {
        return Result.success(probeStrategyService.getStrategy());
    }

    @PreAuthorize("@perm.has('host:probe')")
    @PutMapping("/probe-strategy")
    public Result<ProbeStrategyDTO> updateProbeStrategy(@Valid @RequestBody ProbeStrategyDTO dto) {
        return Result.success(probeStrategyService.updateStrategy(dto));
    }
}
