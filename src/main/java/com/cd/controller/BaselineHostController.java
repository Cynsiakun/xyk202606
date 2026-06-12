package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.dto.BaselineActionResponseDTO;
import com.cd.dto.BaselineHostOverviewDTO;
import com.cd.dto.BaselineHostResultItemDTO;
import com.cd.dto.BaselineRemediationRequestDTO;
import com.cd.dto.BaselineScanHostsRequestDTO;
import com.cd.dto.BaselineWorkorderRequestDTO;
import com.cd.service.BaselineHostService;
import com.cd.service.BaselineRemediationService;
import com.cd.service.BaselineWorkorderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/baseline")
@RequiredArgsConstructor
public class BaselineHostController {

    private final BaselineHostService baselineHostService;
    private final BaselineRemediationService baselineRemediationService;
    private final BaselineWorkorderService baselineWorkorderService;

    @PreAuthorize("@perm.has('baseline:view')")
    @GetMapping("/hosts")
    public Result<PageResult<BaselineHostOverviewDTO>> listHosts(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page必须大于0") Integer page,
            @RequestParam(defaultValue = "12") @Min(value = 1, message = "size必须大于0") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String level) {
        return Result.success(baselineHostService.listHostOverview(page, size, keyword, level));
    }

    @PreAuthorize("@perm.has('baseline:view')")
    @GetMapping("/hosts/{hostId}/results")
    public Result<List<BaselineHostResultItemDTO>> listHostResults(
            @PathVariable @Min(value = 1, message = "hostId必须大于0") Long hostId,
            @RequestParam(defaultValue = "true") boolean onlyFail) {
        return Result.success(baselineHostService.listHostResults(hostId, onlyFail));
    }

    @PreAuthorize("@perm.has('baseline:create')")
    @PostMapping("/hosts/scan")
    public Result<BaselineActionResponseDTO> scanHosts(@Valid @RequestBody BaselineScanHostsRequestDTO request) {
        return Result.success(baselineHostService.scanHosts(request.getHostIds()));
    }

    @PreAuthorize("@perm.has('baseline:remediate')")
    @PostMapping("/remediations")
    public Result<BaselineActionResponseDTO> remediate(@Valid @RequestBody BaselineRemediationRequestDTO request) {
        return Result.success(baselineRemediationService.remediate(request.getResultIds()));
    }

    @PreAuthorize("@perm.has('baseline:remediate')")
    @PostMapping("/remediations/rollback")
    public Result<BaselineActionResponseDTO> rollback(@Valid @RequestBody BaselineRemediationRequestDTO request) {
        return Result.success(baselineRemediationService.rollback(request.getResultIds()));
    }

    @PreAuthorize("@perm.has('baseline:create')")
    @PostMapping("/results/recheck")
    public Result<BaselineActionResponseDTO> recheck(@Valid @RequestBody BaselineRemediationRequestDTO request) {
        return Result.success(baselineHostService.recheckResults(request.getResultIds()));
    }

    @PreAuthorize("@perm.has('baseline:remediate')")
    @PostMapping("/workorders")
    public Result<BaselineActionResponseDTO> createWorkorder(@Valid @RequestBody BaselineWorkorderRequestDTO request) {
        return Result.success(baselineWorkorderService.create(
                request.getResultIds(), request.getAssignee(), request.getRemark()));
    }
}
