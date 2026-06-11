package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.dto.HostOptionDTO;
import com.cd.dto.LoginSecurityLogDetailDTO;
import com.cd.dto.LoginSecurityLogItemDTO;
import com.cd.dto.LoginSecurityLogQueryDTO;
import com.cd.dto.LoginSecurityLogStatDTO;
import com.cd.service.LoginSecurityLogViewService;
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
 * 登录日志：浏览 {@code login_security_logs}（Windows 登录安全事件分流）。
 * 全部走服务端分页 / 筛选 / 排序。
 */
@Validated
@RestController
@RequestMapping("/api/login-security-log")
@RequiredArgsConstructor
public class LoginSecurityLogController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LoginSecurityLogViewService service;

    @PreAuthorize("@perm.has('login-security-log:view')")
    @GetMapping("/list")
    public Result<PageResult<LoginSecurityLogItemDTO>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be greater than 0") Integer page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be greater than 0") Integer size,
            @RequestParam(required = false) String loginResult,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer eventId,
            @RequestParam(required = false) Long hostId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String sourceIp,
            @RequestParam(required = false) Integer loginType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        LoginSecurityLogQueryDTO query = buildQuery(loginResult, keyword, eventId, hostId, username, sourceIp, loginType, startTime, endTime);
        return Result.success(service.page(query, page, size, sortField, sortOrder));
    }

    @PreAuthorize("@perm.has('login-security-log:view')")
    @GetMapping("/stats")
    public Result<LoginSecurityLogStatDTO> stats() {
        return Result.success(service.stats());
    }

    @PreAuthorize("@perm.has('login-security-log:view')")
    @GetMapping("/detail/{id}")
    public Result<LoginSecurityLogDetailDTO> detail(@PathVariable @Min(1) Long id) {
        return Result.success(service.detail(id));
    }

    @PreAuthorize("@perm.has('login-security-log:view')")
    @GetMapping("/hosts")
    public Result<List<HostOptionDTO>> hosts() {
        return Result.success(service.hostOptions());
    }

    @PreAuthorize("@perm.has('login-security-log:view')")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String loginResult,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer eventId,
            @RequestParam(required = false) Long hostId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String sourceIp,
            @RequestParam(required = false) Integer loginType,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        LoginSecurityLogQueryDTO query = buildQuery(loginResult, keyword, eventId, hostId, username, sourceIp, loginType, startTime, endTime);
        byte[] content = service.exportCsv(query, sortField, sortOrder);
        String fileName = URLEncoder.encode("login_security_logs.csv", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .body(content);
    }

    private LoginSecurityLogQueryDTO buildQuery(String loginResult, String keyword, Integer eventId, Long hostId,
                                                String username, String sourceIp, Integer loginType,
                                                String startTime, String endTime) {
        LoginSecurityLogQueryDTO query = new LoginSecurityLogQueryDTO();
        query.setLoginResult(trimToNull(loginResult));
        query.setKeyword(trimToNull(keyword));
        query.setEventId(eventId);
        query.setHostId(hostId);
        query.setUsername(trimToNull(username));
        query.setSourceIp(trimToNull(sourceIp));
        query.setLoginType(loginType);
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
