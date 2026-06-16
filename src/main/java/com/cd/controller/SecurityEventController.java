package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.dto.AlertIdsDTO;
import com.cd.dto.HostOptionDTO;
import com.cd.dto.SecurityEventDetailDTO;
import com.cd.dto.SecurityEventItemDTO;
import com.cd.dto.SecurityEventQueryDTO;
import com.cd.dto.SecurityEventStatDTO;
import com.cd.service.SecurityEventService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 安全事件中心：浏览 / 处置 {@code security_alerts} 告警。
 * 全部走服务端分页 / 筛选 / 排序。
 */
@Validated
@RestController
@RequestMapping("/api/security-event")
@RequiredArgsConstructor
@PreAuthorize("@licenseGuard.hasFeature('LOG')")
public class SecurityEventController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SecurityEventService securityEventService;

    @PreAuthorize("@perm.has('security-alert:view')")
    @GetMapping("/list")
    public Result<PageResult<SecurityEventItemDTO>> list(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) Integer size,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer eventId,
            @RequestParam(required = false) Long hostId,
            @RequestParam(required = false) String alertName,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        SecurityEventQueryDTO query = buildQuery(level, status, keyword, eventId, hostId, alertName, startTime, endTime);
        return Result.success(securityEventService.page(query, page, size, sortField, sortOrder));
    }

    @PreAuthorize("@perm.has('security-alert:view')")
    @GetMapping("/stats")
    public Result<SecurityEventStatDTO> stats() {
        return Result.success(securityEventService.stats());
    }

    @PreAuthorize("@perm.has('security-alert:view')")
    @GetMapping("/detail/{id}")
    public Result<SecurityEventDetailDTO> detail(@PathVariable @Min(1) Long id) {
        return Result.success(securityEventService.detail(id));
    }

    @PreAuthorize("@perm.has('security-alert:view')")
    @GetMapping("/hosts")
    public Result<List<HostOptionDTO>> hosts() {
        return Result.success(securityEventService.hostOptions());
    }

    @PreAuthorize("@perm.has('security-alert:handle')")
    @PostMapping("/ack")
    public Result<Integer> ack(@Valid @RequestBody AlertIdsDTO dto) {
        return Result.success(securityEventService.ack(dto.getIds()));
    }

    @PreAuthorize("@perm.has('security-alert:handle')")
    @PostMapping("/resolve")
    public Result<Integer> resolve(@Valid @RequestBody AlertIdsDTO dto) {
        return Result.success(securityEventService.resolve(dto.getIds()));
    }

    @PreAuthorize("@perm.has('security-alert:view')")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer eventId,
            @RequestParam(required = false) Long hostId,
            @RequestParam(required = false) String alertName,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        SecurityEventQueryDTO query = buildQuery(level, status, keyword, eventId, hostId, alertName, startTime, endTime);
        byte[] content = securityEventService.exportCsv(query, sortField, sortOrder);
        String fileName = URLEncoder.encode("security_events.csv", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .body(content);
    }

    private SecurityEventQueryDTO buildQuery(String level, String status, String keyword, Integer eventId,
                                             Long hostId, String alertName, String startTime, String endTime) {
        SecurityEventQueryDTO query = new SecurityEventQueryDTO();
        query.setLevel(trimToNull(level));
        query.setStatus(trimToNull(status));
        query.setKeyword(trimToNull(keyword));
        query.setEventId(eventId);
        query.setHostId(hostId);
        query.setAlertName(trimToNull(alertName));
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
