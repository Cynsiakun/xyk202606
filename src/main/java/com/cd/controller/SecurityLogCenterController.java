package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.dto.EventLogDetailDTO;
import com.cd.dto.EventLogItemDTO;
import com.cd.dto.EventLogQueryDTO;
import com.cd.dto.EventLogStatDTO;
import com.cd.dto.HostOptionDTO;
import com.cd.service.SecurityLogCenterService;
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
 * 日志中心：浏览 {@code windows_event_logs} 原始日志。
 * 全部走服务端分页 / 筛选 / 排序，列表查询不返回 raw_xml。
 */
@Validated
@RestController
@RequestMapping("/api/security-log-center")
@RequiredArgsConstructor
public class SecurityLogCenterController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SecurityLogCenterService securityLogCenterService;

    @PreAuthorize("@perm.has('security-log:view')")
    @GetMapping("/list")
    public Result<PageResult<EventLogItemDTO>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be greater than 0") Integer page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be greater than 0") Integer size,
            @RequestParam(required = false) String logType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer eventId,
            @RequestParam(required = false) Long hostId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        EventLogQueryDTO query = buildQuery(logType, keyword, eventId, hostId, username, level, startTime, endTime);
        return Result.success(securityLogCenterService.page(query, page, size, sortField, sortOrder));
    }

    @PreAuthorize("@perm.has('security-log:view')")
    @GetMapping("/stats")
    public Result<EventLogStatDTO> stats() {
        return Result.success(securityLogCenterService.stats());
    }

    @PreAuthorize("@perm.has('security-log:view')")
    @GetMapping("/detail/{id}")
    public Result<EventLogDetailDTO> detail(@PathVariable @Min(1) Long id) {
        return Result.success(securityLogCenterService.detail(id));
    }

    @PreAuthorize("@perm.has('security-log:view')")
    @GetMapping("/hosts")
    public Result<List<HostOptionDTO>> hosts() {
        return Result.success(securityLogCenterService.hostOptions());
    }

    @PreAuthorize("@perm.has('security-log:view')")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String logType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer eventId,
            @RequestParam(required = false) Long hostId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        EventLogQueryDTO query = buildQuery(logType, keyword, eventId, hostId, username, level, startTime, endTime);
        byte[] content = securityLogCenterService.exportCsv(query, sortField, sortOrder);
        String fileName = URLEncoder.encode("event_logs.csv", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .body(content);
    }

    private EventLogQueryDTO buildQuery(String logType, String keyword, Integer eventId, Long hostId,
                                        String username, String level, String startTime, String endTime) {
        EventLogQueryDTO query = new EventLogQueryDTO();
        query.setLogType(trimToNull(logType));
        query.setKeyword(trimToNull(keyword));
        query.setEventId(eventId);
        query.setHostId(hostId);
        query.setUsername(trimToNull(username));
        query.setLevel(trimToNull(level));
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
