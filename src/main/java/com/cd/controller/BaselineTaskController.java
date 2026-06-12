package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
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
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page必须大于0") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size必须大于0") Integer size,
            @RequestParam(required = false) String status) {
        return Result.success(baselineQueryService.listTasks(page, size, status));
    }

    @PreAuthorize("@perm.has('baseline:view')")
    @GetMapping("/tasks/{id}/result")
    public Result<BaselineTaskResultOverviewDTO> getResult(
            @PathVariable @Min(value = 1, message = "id必须大于0") Long id) {
        return Result.success(baselineQueryService.getResultOverview(id));
    }

    @PreAuthorize("@perm.has('baseline:view')")
    @GetMapping("/tasks/{id}/problem-hosts")
    public Result<PageResult<BaselineProblemHostDTO>> listProblemHosts(
            @PathVariable @Min(value = 1, message = "id必须大于0") Long id,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page必须大于0") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size必须大于0") Integer size) {
        return Result.success(baselineQueryService.listProblemHosts(id, page, size));
    }

    @PreAuthorize("@perm.has('baseline:view')")
    @GetMapping("/rules")
    public Result<List<BaselineRuleOptionDTO>> listRules(@RequestParam(required = false) String keyword) {
        return Result.success(baselineQueryService.listRuleOptions(keyword));
    }
}
