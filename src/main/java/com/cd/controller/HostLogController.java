package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.dto.EventLogDetailDTO;
import com.cd.dto.EventLogItemDTO;
import com.cd.dto.EventLogQueryDTO;
import com.cd.dto.HostOptionDTO;
import com.cd.service.SecurityLogCenterService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 主机日志管理：从主机维度查看 {@code windows_event_logs}。
 *
 * <p>查询逻辑完全复用 {@link SecurityLogCenterService}（同一张表、同样的服务端分页/筛选/排序），
 * 本控制器只是按「主机 + 日志类型」维度收口入参，并使用独立的 {@code host-log:view} 权限。</p>
 */
@Validated
@RestController
@RequestMapping("/api/host-log")
@RequiredArgsConstructor
public class HostLogController {

    private final SecurityLogCenterService securityLogCenterService;

    /** 主机下拉/列表选项。 */
    @PreAuthorize("@perm.has('host-log:view')")
    @GetMapping("/hosts")
    public Result<List<HostOptionDTO>> hosts() {
        return Result.success(securityLogCenterService.hostOptions());
    }

    /** 指定主机 + 日志类型的分页日志。 */
    @PreAuthorize("@perm.has('host-log:view')")
    @GetMapping("/list")
    public Result<PageResult<EventLogItemDTO>> list(
            @RequestParam @Min(value = 1, message = "hostId is required") Long hostId,
            @RequestParam(required = false) String logType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be greater than 0") Integer page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "size must be greater than 0") Integer size,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        EventLogQueryDTO query = new EventLogQueryDTO();
        query.setHostId(hostId);
        query.setLogType(trimToNull(logType));
        query.setKeyword(trimToNull(keyword));
        return Result.success(securityLogCenterService.page(query, page, size, sortField, sortOrder));
    }

    /** 单条日志详情（含原始 XML）。 */
    @PreAuthorize("@perm.has('host-log:view')")
    @GetMapping("/detail/{id}")
    public Result<EventLogDetailDTO> detail(@PathVariable @Min(1) Long id) {
        return Result.success(securityLogCenterService.detail(id));
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
