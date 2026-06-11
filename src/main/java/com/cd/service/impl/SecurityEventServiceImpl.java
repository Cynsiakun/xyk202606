package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.dto.HostOptionDTO;
import com.cd.dto.PopupAlertDTO;
import com.cd.dto.SecurityEventDetailDTO;
import com.cd.dto.SecurityEventItemDTO;
import com.cd.dto.SecurityEventQueryDTO;
import com.cd.dto.SecurityEventStatDTO;
import com.cd.mapper.SecurityEventMapper;
import com.cd.service.SecurityEventService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SecurityEventServiceImpl implements SecurityEventService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 排序字段白名单：前端字段名 -> 数据库列名，杜绝 ORDER BY 注入。 */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "eventTime", "event_time",
            "createTime", "create_time",
            "level", "level",
            "status", "status",
            "id", "id"
    );

    private static final int EXPORT_LIMIT = 50000;
    private static final int POPUP_LIMIT = 50;

    private final SecurityEventMapper mapper;

    @Override
    public PageResult<SecurityEventItemDTO> page(SecurityEventQueryDTO query, int page, int size, String sortField, String sortOrder) {
        applySort(query, sortField, sortOrder);
        query.setSize(size);
        query.setOffset((page - 1) * size);
        long total = mapper.countPage(query);
        List<SecurityEventItemDTO> list = total == 0 ? List.of() : mapper.selectPage(query);
        return new PageResult<>(total, list);
    }

    @Override
    public SecurityEventStatDTO stats() {
        SecurityEventStatDTO stat = new SecurityEventStatDTO();
        stat.setCritical(mapper.countByLevel("Critical"));
        stat.setHigh(mapper.countByLevel("High"));
        stat.setMedium(mapper.countByLevel("Medium"));
        stat.setUntreated(mapper.countByStatus("new"));
        return stat;
    }

    @Override
    public SecurityEventDetailDTO detail(Long id) {
        return mapper.selectDetailById(id);
    }

    @Override
    public List<HostOptionDTO> hostOptions() {
        return mapper.selectHostOptions();
    }

    @Override
    public int ack(List<Long> ids) {
        // 仅把 new 推进为 acked
        return mapper.updateStatus(ids, "acked", List.of("new"));
    }

    @Override
    public int resolve(List<Long> ids) {
        // new / acked 都可直接处理为 resolved
        return mapper.updateStatus(ids, "resolved", List.of("new", "acked"));
    }

    @Override
    public byte[] exportCsv(SecurityEventQueryDTO query, String sortField, String sortOrder) {
        applySort(query, sortField, sortOrder);
        List<SecurityEventItemDTO> rows = mapper.selectForExport(query, EXPORT_LIMIT);

        StringWriter writer = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader("ID", "事件时间", "主机", "IP", "事件ID", "告警名称", "等级", "风险分", "状态", "描述")
                .build();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (SecurityEventItemDTO row : rows) {
                printer.printRecord(
                        row.getId(),
                        row.getEventTime() == null ? "" : TIME_FORMAT.format(row.getEventTime()),
                        row.getHostname(),
                        row.getIpv4(),
                        row.getEventId(),
                        row.getAlertName(),
                        row.getLevel(),
                        row.getRiskScore(),
                        row.getStatus(),
                        row.getDescription()
                );
            }
        } catch (IOException e) {
            throw new UncheckedIOException("导出安全事件失败", e);
        }

        byte[] body = writer.toString().getBytes(StandardCharsets.UTF_8);
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] result = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(body, 0, result, bom.length, body.length);
        return result;
    }

    @Override
    public Long currentMaxId() {
        Long maxId = mapper.selectMaxId();
        return maxId == null ? 0L : maxId;
    }

    @Override
    public List<PopupAlertDTO> newHighCritical(Long afterId) {
        return mapper.selectNewHighCritical(afterId == null ? 0L : afterId, POPUP_LIMIT);
    }

    @Override
    public List<PopupAlertDTO> recentHighCritical() {
        return mapper.selectRecentHighCritical(20);
    }

    private void applySort(SecurityEventQueryDTO query, String sortField, String sortOrder) {
        // SORT_COLUMNS 由 Map.of 创建，不允许 null 键查找，先判空
        String column = sortField == null ? null : SORT_COLUMNS.get(sortField);
        query.setSortColumn(column != null ? column : "event_time");
        query.setSortDirection("asc".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC");
    }
}
