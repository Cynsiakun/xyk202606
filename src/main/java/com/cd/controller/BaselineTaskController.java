package com.cd.controller;

import com.cd.common.Result;
import com.cd.dto.BaselineTaskCreateRequestDTO;
import com.cd.dto.BaselineTaskDispatchResponseDTO;
import com.cd.service.BaselineTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/baseline/tasks")
@RequiredArgsConstructor
public class BaselineTaskController {

    private final BaselineTaskService baselineTaskService;

    @PostMapping
    public Result<BaselineTaskDispatchResponseDTO> create(@Valid @RequestBody BaselineTaskCreateRequestDTO request) {
        return Result.success(baselineTaskService.createAndDispatch(request));
    }
}
