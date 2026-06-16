package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.dto.BaselineHostResultItemDTO;
import com.cd.dto.BaselineProblemHostDTO;
import com.cd.dto.BaselineRuleOptionDTO;
import com.cd.dto.BaselineTaskCreateRequestDTO;
import com.cd.dto.BaselineTaskDispatchResponseDTO;
import com.cd.dto.BaselineTaskListItemDTO;
import com.cd.dto.BaselineTaskResultOverviewDTO;
import com.cd.service.BaselineQueryService;
import com.cd.service.BaselineTaskService;
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
@PreAuthorize("@licenseGuard.hasFeature('BASELINE')")
public class BaselineTaskController {

    private final BaselineTaskService baselineTaskService;
    private final BaselineQueryService baselineQueryService;

    @PreAuthorize("@perm.has('baseline:create')")
    @PostMapping("/tasks")
    public Result<BaselineTaskDispatchResponseDTO> create(@Valid @RequestBody BaselineTaskCreateRequestDTO request) {
        return Result.success(baselineTaskService.createAndDispatch(request));
    }

    @PreAuthorize("@perm.has('baseline:view')")
    @GetMapping("/tasks")
    public Result<PageResult<BaselineTaskListItemDTO>> listTasks(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be greater than 0") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size must be greater than 0") Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String executeType,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String status) {
        return Result.success(baselineQueryService.listTasks(page, size, keyword, executeType, taskType, status));
    }

    @PreAuthorize("@perm.has('baseline:view')")
    @GetMapping("/tasks/{id}/result")
    public Result<BaselineTaskResultOverviewDTO> getResult(
            @PathVariable @Min(value = 1, message = "id must be greater than 0") Long id) {
        return Result.success(baselineQueryService.getResultOverview(id));
    }

    @PreAuthorize("@perm.has('baseline:view')")
    @GetMapping("/tasks/{id}/problem-hosts")
    public Result<PageResult<BaselineProblemHostDTO>> listProblemHosts(
            @PathVariable @Min(value = 1, message = "id must be greater than 0") Long id,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be greater than 0") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size must be greater than 0") Integer size) {
        return Result.success(baselineQueryService.listProblemHosts(id, page, size));
    }

    @PreAuthorize("@perm.has('baseline:view')")
    @GetMapping("/tasks/{taskId}/hosts/{hostId}/results")
    public Result<List<BaselineHostResultItemDTO>> listTaskHostResults(
            @PathVariable @Min(value = 1, message = "taskId must be greater than 0") Long taskId,
            @PathVariable @Min(value = 1, message = "hostId must be greater than 0") Long hostId) {
        return Result.success(baselineQueryService.listTaskHostResults(taskId, hostId));
    }

    @PreAuthorize("@perm.has('baseline:view')")
    @GetMapping("/rules")
    public Result<List<BaselineRuleOptionDTO>> listRules(@RequestParam(required = false) String keyword) {
        return Result.success(baselineQueryService.listRuleOptions(keyword));
    }

    @PreAuthorize("@perm.has('baseline:view')")
    @GetMapping("/tasks/{id}/export")
    public ResponseEntity<byte[]> exportTaskResult(
            @PathVariable @Min(value = 1, message = "id must be greater than 0") Long id,
            @RequestParam(defaultValue = "csv") String format) {
        String normalized = normalizeExportFormat(format);
        byte[] content;
        if ("pdf".equals(normalized)) {
            content = baselineQueryService.exportTaskResultPdf(id);
        } else if ("html".equals(normalized)) {
            content = baselineQueryService.exportTaskResultHtml(id);
        } else {
            content = baselineQueryService.exportTaskResultCsv(id);
        }
        String fileName = "baseline_task_" + id + "_results." + normalized;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(exportMediaType(normalized))
                .body(content);
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
