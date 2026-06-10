package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.dto.EventLogDetailDTO;
import com.cd.dto.EventLogItemDTO;
import com.cd.dto.EventLogQueryDTO;
import com.cd.dto.EventLogStatDTO;
import com.cd.dto.HostOptionDTO;
import com.cd.mapper.SecurityLogCenterMapper;
import com.cd.service.SecurityLogCenterService;
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
public class SecurityLogCenterServiceImpl implements SecurityLogCenterService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 排序字段白名单：前端字段名 -> 数据库列名，杜绝 ORDER BY 注入。 */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "eventTime", "event_time",
            "eventId", "event_id",
            "id", "id",
            "recordNumber", "record_number"
    );

    /** 导出上限，防止百万级一次性拉爆内存。 */
    private static final int EXPORT_LIMIT = 50000;

    private final SecurityLogCenterMapper mapper;

    @Override
    public PageResult<EventLogItemDTO> page(EventLogQueryDTO query, int page, int size, String sortField, String sortOrder) {
        applySort(query, sortField, sortOrder);
        query.setSize(size);
        query.setOffset((page - 1) * size);
        long total = mapper.countPage(query);
        List<EventLogItemDTO> list = total == 0 ? List.of() : mapper.selectPage(query);
        return new PageResult<>(total, list);
    }

    @Override
    public EventLogStatDTO stats() {
        EventLogStatDTO stat = new EventLogStatDTO();
        stat.setTodaySecurity(mapper.countTodayByType("Security"));
        stat.setTodaySystem(mapper.countTodayByType("System"));
        stat.setTodayApplication(mapper.countTodayByType("Application"));
        stat.setTodayError(mapper.countTodayError());
        return stat;
    }

    @Override
    public EventLogDetailDTO detail(Long id) {
        return mapper.selectDetailById(id);
    }

    @Override
    public List<HostOptionDTO> hostOptions() {
        return mapper.selectHostOptions();
    }

    @Override
    public byte[] exportCsv(EventLogQueryDTO query, String sortField, String sortOrder) {
        applySort(query, sortField, sortOrder);
        List<EventLogItemDTO> rows = mapper.selectForExport(query, EXPORT_LIMIT);

        StringWriter writer = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader("ID", "主机", "IP", "日志类型", "事件ID", "事件时间", "用户名", "级别", "记录号", "摘要")
                .build();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (EventLogItemDTO row : rows) {
                printer.printRecord(
                        row.getId(),
                        row.getHostname(),
                        row.getIpv4(),
                        row.getLogType(),
                        row.getEventId(),
                        row.getEventTime() == null ? "" : TIME_FORMAT.format(row.getEventTime()),
                        row.getUsername(),
                        row.getLevel(),
                        row.getRecordNumber(),
                        row.getMessage()
                );
            }
        } catch (IOException e) {
            throw new UncheckedIOException("导出日志失败", e);
        }

        // 加 UTF-8 BOM，保证 Excel 直接打开不乱码
        byte[] body = writer.toString().getBytes(StandardCharsets.UTF_8);
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] result = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(body, 0, result, bom.length, body.length);
        return result;
    }

    private void applySort(EventLogQueryDTO query, String sortField, String sortOrder) {
        // 注意：SORT_COLUMNS 由 Map.of 创建，不允许 null 键查找，故先判空
        String column = sortField == null ? null : SORT_COLUMNS.get(sortField);
        query.setSortColumn(column != null ? column : "event_time");
        query.setSortDirection("asc".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC");
    }
}
