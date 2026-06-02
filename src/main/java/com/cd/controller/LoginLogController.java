package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.dto.LoginLogResponseDTO;
import com.cd.service.LoginLogService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/login-log")
@RequiredArgsConstructor
public class LoginLogController {

    private final LoginLogService loginLogService;

    @GetMapping("/list")
    public Result<PageResult<LoginLogResponseDTO>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be greater than 0") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size must be greater than 0") Integer size,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) Integer status) {
        return Result.success(loginLogService.list(page, size, userName, status));
    }
}
