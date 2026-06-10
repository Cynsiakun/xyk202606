package com.cd.controller;

import com.cd.common.Result;
import com.cd.dto.PatchSecurityActionResultDTO;
import com.cd.dto.VulnAffectedHostDTO;
import com.cd.dto.VulnDetectionSummaryDTO;
import com.cd.dto.VulnHostOverviewDTO;
import com.cd.dto.VulnOverviewDTO;
import com.cd.dto.VulnResultBatchActionRequestDTO;
import com.cd.entity.HostVulnResultEntity;
import com.cd.service.VulnDetectionService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/vuln-detection")
@RequiredArgsConstructor
public class VulnDetectionController {

    private final VulnDetectionService vulnDetectionService;

    @PreAuthorize("@perm.has('vuln-detection:view')")
    @GetMapping("/summary")
    public Result<VulnDetectionSummaryDTO> summary() {
        return Result.success(vulnDetectionService.summary());
    }

    @PreAuthorize("@perm.has('vuln-detection:view')")
    @GetMapping("/hosts")
    public Result<List<VulnHostOverviewDTO>> hosts() {
        return Result.success(vulnDetectionService.listHostOverviews());
    }

    @PreAuthorize("@perm.has('vuln-detection:view')")
    @GetMapping("/vulnerabilities")
    public Result<List<VulnOverviewDTO>> vulnerabilities() {
        return Result.success(vulnDetectionService.listVulnOverviews());
    }

    @PreAuthorize("@perm.has('vuln-detection:view')")
    @GetMapping("/vulnerabilities/{ruleId}/hosts")
    public Result<List<VulnAffectedHostDTO>> affectedHosts(
            @PathVariable @Min(value = 1, message = "ruleId必须大于0") Long ruleId) {
        return Result.success(vulnDetectionService.listAffectedHosts(ruleId));
    }

    @PreAuthorize("@perm.has('vuln-detection:view')")
    @GetMapping("/hosts/{hostId}/results")
    public Result<List<HostVulnResultEntity>> hostResults(
            @PathVariable @Min(value = 1, message = "hostId必须大于0") Long hostId) {
        return Result.success(vulnDetectionService.listActiveResults(hostId));
    }

    @PreAuthorize("@perm.has('vuln-detection:analyze')")
    @PostMapping("/evaluate-all")
    public Result<PatchSecurityActionResultDTO> evaluateAll() {
        return Result.success(vulnDetectionService.evaluateAllHosts());
    }

    @PreAuthorize("@perm.has('vuln-detection:analyze')")
    @PostMapping("/hosts/{hostId}/evaluate")
    public Result<List<HostVulnResultEntity>> evaluateHost(
            @PathVariable @Min(value = 1, message = "hostId必须大于0") Long hostId) {
        return Result.success(vulnDetectionService.evaluateHost(hostId));
    }

    @PreAuthorize("@perm.has('vuln-detection:analyze')")
    @PostMapping("/results/ignore")
    public Result<Map<String, Object>> ignoreResults(@Valid @RequestBody VulnResultBatchActionRequestDTO request) {
        int updated = vulnDetectionService.ignoreResults(request.getResultIds());
        return Result.success(Map.of("updated", updated));
    }
}
