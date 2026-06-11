package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.dto.HostOptionDTO;
import com.cd.dto.LoginSecurityLogDetailDTO;
import com.cd.dto.LoginSecurityLogItemDTO;
import com.cd.dto.LoginSecurityLogQueryDTO;
import com.cd.dto.LoginSecurityLogStatDTO;
import com.cd.mapper.LoginSecurityLogViewMapper;
import com.cd.service.LoginSecurityLogViewService;
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
public class LoginSecurityLogViewServiceImpl implements LoginSecurityLogViewService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 排序字段白名单：前端字段名 -> 数据库列名，杜绝 ORDER BY 注入。 */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "eventTime", "event_time",
            "eventId", "event_id",
            "id", "id"
    );

    private static final int EXPORT_LIMIT = 50000;

    private final LoginSecurityLogViewMapper mapper;

    @Override
    public PageResult<LoginSecurityLogItemDTO> page(LoginSecurityLogQueryDTO query, int page, int size, String sortField, String sortOrder) {
        applySort(query, sortField, sortOrder);
        query.setSize(size);
        query.setOffset((page - 1) * size);
        long total = mapper.countPage(query);
        List<LoginSecurityLogItemDTO> list = total == 0 ? List.of() : mapper.selectPage(query);
        return new PageResult<>(total, list);
    }

    @Override
    public LoginSecurityLogStatDTO stats() {
        LoginSecurityLogStatDTO stat = new LoginSecurityLogStatDTO();
        stat.setTodaySuccess(mapper.countTodayByResult("success"));
        stat.setTodayFail(mapper.countTodayByResult("fail"));
        stat.setTodayLogout(mapper.countTodayByResult("logout"));
        stat.setTodayElevated(mapper.countTodayElevated());
        return stat;
    }

    @Override
    public LoginSecurityLogDetailDTO detail(Long id) {
        return mapper.selectDetailById(id);
    }

    @Override
    public List<HostOptionDTO> hostOptions() {
        return mapper.selectHostOptions();
    }

    @Override
    public byte[] exportCsv(LoginSecurityLogQueryDTO query, String sortField, String sortOrder) {
        applySort(query, sortField, sortOrder);
        List<LoginSecurityLogItemDTO> rows = mapper.selectForExport(query, EXPORT_LIMIT);

        StringWriter writer = new StringWriter();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader("ID", "事件时间", "主机", "IP", "用户名", "登录结果", "登录类型", "源IP", "进程名", "是否提权", "EventID", "来源日志ID")
                .build();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            for (LoginSecurityLogItemDTO row : rows) {
                printer.printRecord(
                        row.getId(),
                        row.getEventTime() == null ? "" : TIME_FORMAT.format(row.getEventTime()),
                        row.getHostname(),
                        row.getIpv4(),
                        row.getUsername(),
                        row.getLoginResult(),
                        row.getLoginType(),
                        row.getSourceIp(),
                        row.getProcessName(),
                        (row.getIsElevated() != null && row.getIsElevated() == 1) ? "是" : "否",
                        row.getEventId(),
                        row.getSourceLogId()
                );
            }
        } catch (IOException e) {
            throw new UncheckedIOException("导出登录日志失败", e);
        }

        byte[] body = writer.toString().getBytes(StandardCharsets.UTF_8);
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] result = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(body, 0, result, bom.length, body.length);
        return result;
    }

    private void applySort(LoginSecurityLogQueryDTO query, String sortField, String sortOrder) {
        // SORT_COLUMNS 由 Map.of 创建，不允许 null 键查找，故先判空
        String column = sortField == null ? null : SORT_COLUMNS.get(sortField);
        query.setSortColumn(column != null ? column : "event_time");
        query.setSortDirection("asc".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC");
    }
}
