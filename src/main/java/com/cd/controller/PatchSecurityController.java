package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.dto.PatchRiskDetailDTO;
import com.cd.dto.PatchRiskHostDTO;
import com.cd.dto.PatchSecurityActionResultDTO;
import com.cd.dto.PatchSecurityAnalyzeRequestDTO;
import com.cd.dto.PatchSecurityScanRequestDTO;
import com.cd.dto.PatchSecuritySummaryDTO;
import com.cd.entity.HostPatchRiskEntity;
import com.cd.service.PatchSecurityService;
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
@RequestMapping("/api/patch-security")
@RequiredArgsConstructor
@PreAuthorize("@licenseGuard.hasFeature('PATCH')")
public class PatchSecurityController {

    private final PatchSecurityService patchSecurityService;

    @PreAuthorize("@perm.has('patch-security:view')")
    @GetMapping("/summary")
    public Result<PatchSecuritySummaryDTO> summary() {
        return Result.success(patchSecurityService.summary());
    }

    @PreAuthorize("@perm.has('patch-security:view')")
    @GetMapping("/risk-hosts")
    public Result<PageResult<PatchRiskHostDTO>> riskHosts(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page必须大于0") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size必须大于0") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String riskType,
            @RequestParam(required = false) Integer pendingReboot,
            @RequestParam(required = false) String osName) {
        return Result.success(patchSecurityService.listRiskHosts(
                page, size, keyword, riskLevel, riskType, pendingReboot, osName));
    }

    @PreAuthorize("@perm.has('patch-security:view')")
    @GetMapping("/hosts/{hostId}/risks")
    public Result<List<PatchRiskDetailDTO>> hostRisks(
            @PathVariable @Min(value = 1, message = "hostId必须大于0") Long hostId) {
        return Result.success(patchSecurityService.listHostRisks(hostId));
    }

    @PreAuthorize("@perm.has('patch-security:analyze')")
    @PostMapping("/analyze")
    public Result<PatchSecurityActionResultDTO> analyze(@RequestBody(required = false) PatchSecurityAnalyzeRequestDTO dto) {
        List<Long> hostIds = dto == null ? null : dto.getHostIds();
        return Result.success(patchSecurityService.analyze(hostIds));
    }

    @PreAuthorize("@perm.has('patch-security:analyze')")
    @PostMapping("/hosts/{hostId}/analyze")
    public Result<List<HostPatchRiskEntity>> analyzeHost(
            @PathVariable @Min(value = 1, message = "hostId必须大于0") Long hostId) {
        return Result.success(patchSecurityService.analyzeHost(hostId));
    }

    @PreAuthorize("@perm.has('patch-security:scan')")
    @PostMapping("/scan")
    public Result<PatchSecurityActionResultDTO> scan(@RequestBody(required = false) PatchSecurityScanRequestDTO dto) {
        List<Long> hostIds = dto == null ? null : dto.getHostIds();
        return Result.success(patchSecurityService.scan(hostIds));
    }

    @PreAuthorize("@perm.has('patch-security:scan')")
    @PostMapping("/hosts/{hostId}/scan")
    public Result<PatchSecurityActionResultDTO> scanHost(
            @PathVariable @Min(value = 1, message = "hostId必须大于0") Long hostId) {
        PatchSecurityScanRequestDTO dto = new PatchSecurityScanRequestDTO();
        dto.setHostIds(List.of(hostId));
        return Result.success(patchSecurityService.scan(dto.getHostIds()));
    }
}
