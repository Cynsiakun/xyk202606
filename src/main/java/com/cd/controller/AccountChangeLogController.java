package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.dto.AccountChangeLogDetailDTO;
import com.cd.dto.AccountChangeLogItemDTO;
import com.cd.dto.AccountChangeLogQueryDTO;
import com.cd.dto.AccountChangeLogStatDTO;
import com.cd.dto.HostOptionDTO;
import com.cd.service.AccountChangeLogViewService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 账户变更日志：浏览 {@code account_change_logs}（Windows 账户管理事件分流）。
 * 全部走服务端分页 / 筛选 / 排序。
 */
@Validated
@RestController
@RequestMapping("/api/account-change-log")
@RequiredArgsConstructor
public class AccountChangeLogController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AccountChangeLogViewService service;

    @PreAuthorize("@perm.has('account-change-log:view')")
    @GetMapping("/list")
    public Result<PageResult<AccountChangeLogItemDTO>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be greater than 0") Integer page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be greater than 0") Integer size,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer eventId,
            @RequestParam(required = false) Long hostId,
            @RequestParam(required = false) String operatorUsername,
            @RequestParam(required = false) String targetUsername,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        AccountChangeLogQueryDTO query = buildQuery(actionType, keyword, eventId, hostId, operatorUsername, targetUsername, startTime, endTime);
        return Result.success(service.page(query, page, size, sortField, sortOrder));
    }

    @PreAuthorize("@perm.has('account-change-log:view')")
    @GetMapping("/stats")
    public Result<AccountChangeLogStatDTO> stats() {
        return Result.success(service.stats());
    }

    @PreAuthorize("@perm.has('account-change-log:view')")
    @GetMapping("/detail/{id}")
    public Result<AccountChangeLogDetailDTO> detail(@PathVariable @Min(1) Long id) {
        return Result.success(service.detail(id));
    }

    @PreAuthorize("@perm.has('account-change-log:view')")
    @GetMapping("/hosts")
    public Result<List<HostOptionDTO>> hosts() {
        return Result.success(service.hostOptions());
    }

    @PreAuthorize("@perm.has('account-change-log:view')")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer eventId,
            @RequestParam(required = false) Long hostId,
            @RequestParam(required = false) String operatorUsername,
            @RequestParam(required = false) String targetUsername,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        AccountChangeLogQueryDTO query = buildQuery(actionType, keyword, eventId, hostId, operatorUsername, targetUsername, startTime, endTime);
        byte[] content = service.exportCsv(query, sortField, sortOrder);
        String fileName = URLEncoder.encode("account_change_logs.csv", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .body(content);
    }

    private AccountChangeLogQueryDTO buildQuery(String actionType, String keyword, Integer eventId, Long hostId,
                                                String operatorUsername, String targetUsername,
                                                String startTime, String endTime) {
        AccountChangeLogQueryDTO query = new AccountChangeLogQueryDTO();
        query.setActionType(trimToNull(actionType));
        query.setKeyword(trimToNull(keyword));
        query.setEventId(eventId);
        query.setHostId(hostId);
        query.setOperatorUsername(trimToNull(operatorUsername));
        query.setTargetUsername(trimToNull(targetUsername));
        query.setStartTime(parseTime(startTime));
        query.setEndTime(parseTime(endTime));
        return query;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private LocalDateTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return LocalDateTime.parse(value.trim(), TIME_FORMAT);
    }
}
