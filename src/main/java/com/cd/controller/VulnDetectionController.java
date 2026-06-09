package com.cd.controller;

import com.cd.common.Result;
import com.cd.entity.HostVulnResultEntity;
import com.cd.service.VulnDetectionService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/vuln-detection")
@RequiredArgsConstructor
public class VulnDetectionController {

    private final VulnDetectionService vulnDetectionService;

    @PreAuthorize("@perm.has('vuln-detection:view')")
    @GetMapping("/hosts/{hostId}/results")
    public Result<List<HostVulnResultEntity>> hostResults(
            @PathVariable @Min(value = 1, message = "hostId必须大于0") Long hostId) {
        return Result.success(vulnDetectionService.listActiveResults(hostId));
    }

    @PreAuthorize("@perm.has('vuln-detection:analyze')")
    @PostMapping("/hosts/{hostId}/evaluate")
    public Result<List<HostVulnResultEntity>> evaluateHost(
            @PathVariable @Min(value = 1, message = "hostId必须大于0") Long hostId) {
        return Result.success(vulnDetectionService.evaluateHost(hostId));
    }
}
