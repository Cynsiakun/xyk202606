package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.dto.BaselineActionResponseDTO;
import com.cd.dto.BaselineHostOverviewDTO;
import com.cd.dto.BaselineHostResultItemDTO;
import com.cd.dto.BaselineOperatorOptionDTO;
import com.cd.dto.BaselineRemediationRecordDTO;
import com.cd.dto.BaselineRemediationRequestDTO;
import com.cd.dto.BaselineScanHostsRequestDTO;
import com.cd.dto.BaselineWorkorderCompleteRequestDTO;
import com.cd.dto.BaselineWorkorderDetailDTO;
import com.cd.dto.BaselineWorkorderListItemDTO;
import com.cd.dto.BaselineWorkorderRequestDTO;
import com.cd.service.BaselineHostService;
import com.cd.service.BaselineQueryService;
import com.cd.service.BaselineRemediationService;
import com.cd.service.BaselineWorkorderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final BaselineQueryService baselineQueryService;
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

    @PreAuthorize("@perm.has('baseline:view')")
    @GetMapping("/hosts/{hostId}/export")
    public ResponseEntity<byte[]> exportHostResult(
            @PathVariable @Min(value = 1, message = "hostId must be greater than 0") Long hostId,
            @RequestParam(defaultValue = "csv") String format) {
        String normalized = normalizeExportFormat(format);
        byte[] content;
        if ("pdf".equals(normalized)) {
            content = baselineQueryService.exportHostResultPdf(hostId);
        } else if ("html".equals(normalized)) {
            content = baselineQueryService.exportHostResultHtml(hostId);
        } else {
            content = baselineQueryService.exportHostResultCsv(hostId);
        }
        String fileName = "baseline_host_" + hostId + "_results." + normalized;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(exportMediaType(normalized))
                .body(content);
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

    @PreAuthorize("@perm.has('baseline:view')")
    @GetMapping("/results/{resultId}/remediations")
    public Result<List<BaselineRemediationRecordDTO>> listRemediationRecords(
            @PathVariable @Min(value = 1, message = "resultId必须大于0") Long resultId) {
        return Result.success(baselineRemediationService.listRecords(resultId));
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
                request.getResultIds(), request.getAssigneeId(), request.getRemark()));
    }

    @PreAuthorize("@perm.has('workorder:view')")
    @GetMapping("/workorders")
    public Result<PageResult<BaselineWorkorderListItemDTO>> listWorkorders(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page必须大于0") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size必须大于0") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority) {
        return Result.success(baselineWorkorderService.list(page, size, keyword, status, priority));
    }

    @PreAuthorize("@perm.has('workorder:view')")
    @GetMapping("/workorders/{id}")
    public Result<BaselineWorkorderDetailDTO> getWorkorder(
            @PathVariable @Min(value = 1, message = "id必须大于0") Long id) {
        return Result.success(baselineWorkorderService.detail(id));
    }

    @PreAuthorize("@perm.has('workorder:process')")
    @PostMapping("/workorders/{id}/start")
    public Result<BaselineActionResponseDTO> startWorkorder(
            @PathVariable @Min(value = 1, message = "id必须大于0") Long id) {
        return Result.success(baselineWorkorderService.start(id));
    }

    @PreAuthorize("@perm.has('workorder:complete')")
    @PostMapping("/workorders/{id}/complete")
    public Result<BaselineActionResponseDTO> completeWorkorder(
            @PathVariable @Min(value = 1, message = "id必须大于0") Long id,
            @Valid @RequestBody BaselineWorkorderCompleteRequestDTO request) {
        return Result.success(baselineWorkorderService.complete(id, request.getCloseRemark()));
    }

    @PreAuthorize("@perm.has('workorder:process')")
    @PostMapping("/workorders/{id}/recheck")
    public Result<BaselineActionResponseDTO> recheckWorkorder(
            @PathVariable @Min(value = 1, message = "id必须大于0") Long id) {
        return Result.success(baselineWorkorderService.recheck(id));
    }

    @PreAuthorize("@perm.has('baseline:remediate')")
    @GetMapping("/workorders/operators")
    public Result<List<BaselineOperatorOptionDTO>> listWorkorderOperators() {
        return Result.success(baselineWorkorderService.operatorOptions());
    }

    private String normalizeExportFormat(String format) {
        if (format == null) {
            return "csv";
        }
        String normalized = format.trim().toLowerCase();
        return "pdf".equals(normalized) || "html".equals(normalized) ? normalized : "csv";
    }

    private MediaType exportMediaType(String format) {
        if ("pdf".equals(format)) {
            return MediaType.APPLICATION_PDF;
        }
        if ("html".equals(format)) {
            return MediaType.parseMediaType("text/html; charset=UTF-8");
        }
        return MediaType.parseMediaType("text/csv; charset=UTF-8");
    }
}
