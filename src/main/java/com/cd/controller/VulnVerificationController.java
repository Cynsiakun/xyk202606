package com.cd.controller;

import com.cd.common.Result;
import com.cd.dto.VulnVerificationBatchRequestDTO;
import com.cd.dto.VulnResultBatchActionRequestDTO;
import com.cd.dto.VulnVerificationTaskResponseDTO;
import com.cd.service.VulnVerificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/vuln-verification")
@RequiredArgsConstructor
public class VulnVerificationController {

    private final VulnVerificationService vulnVerificationService;

    @PreAuthorize("@perm.has('vuln-detection:analyze')")
    @PostMapping("/hosts/{hostId}/verify")
    public Result<VulnVerificationTaskResponseDTO> verifyHost(
            @PathVariable @Min(value = 1, message = "hostId必须大于0") Long hostId) {
        return Result.success(vulnVerificationService.verifyHost(hostId));
    }

    @PreAuthorize("@perm.has('vuln-detection:analyze')")
    @PostMapping("/hosts/{hostId}/results/{resultId}/verify")
    public Result<VulnVerificationTaskResponseDTO> verifyResult(
            @PathVariable @Min(value = 1, message = "hostId必须大于0") Long hostId,
            @PathVariable @Min(value = 1, message = "resultId必须大于0") Long resultId) {
        return Result.success(vulnVerificationService.verifyResult(hostId, resultId));
    }

    @PreAuthorize("@perm.has('vuln-detection:analyze')")
    @PostMapping("/results/verify")
    public Result<Map<Long, Long>> verifyResults(@Valid @RequestBody VulnResultBatchActionRequestDTO request) {
        return Result.success(vulnVerificationService.verifyResults(request.getResultIds()));
    }

    @PreAuthorize("@perm.has('vuln-detection:analyze')")
    @PostMapping("/batch-verify")
    public Result<Map<Long, Long>> batchVerify(@Valid @RequestBody VulnVerificationBatchRequestDTO request) {
        return Result.success(vulnVerificationService.batchVerify(request.getHostIds()));
    }

    @PreAuthorize("@perm.has('vuln-detection:analyze')")
    @PostMapping("/retry/{taskId}")
    public Result<VulnVerificationTaskResponseDTO> retry(
            @PathVariable @Min(value = 1, message = "taskId必须大于0") Long taskId) {
        return Result.success(vulnVerificationService.retry(taskId));
    }
}
