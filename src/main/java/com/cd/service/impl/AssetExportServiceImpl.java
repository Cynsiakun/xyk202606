package com.cd.service.impl;

import com.cd.common.license.LicenseFeature;
import com.cd.common.license.LicenseGuard;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.common.security.SecurityUtils;
import com.cd.common.security.TenantContextHolder;
import com.cd.dto.AssetExportDTO;
import com.cd.dto.AssetExportFileDTO;
import com.cd.dto.HostResponseDTO;
import com.cd.entity.AppEntity;
import com.cd.entity.AssetExportLogEntity;
import com.cd.entity.HostEntity;
import com.cd.entity.ProcessEntity;
import com.cd.entity.ServiceEntity;
import com.cd.mapper.AppMapper;
import com.cd.mapper.AssetExportLogMapper;
import com.cd.mapper.HostMapper;
import com.cd.mapper.ProcessMapper;
import com.cd.mapper.ServiceMapper;
import com.cd.service.AssetExportService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AssetExportServiceImpl implements AssetExportService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final List<String> RISK_LEVELS = List.of("HIGH", "MEDIUM", "LOW", "NONE");
    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS = new TypeReference<>() {
    };

    private final HostMapper hostMapper;
    private final ProcessMapper processMapper;
    private final ServiceMapper serviceMapper;
    private final AppMapper appMapper;
    private final AssetExportLogMapper assetExportLogMapper;
    private final LicenseGuard licenseGuard;

    @Override
    public AssetExportDTO exportJson(Long hostId, String ipAddress) {
        licenseGuard.requireFeature(LicenseFeature.ASSET);
        AssetExportDTO exportData = buildExportData(hostId);
        recordExport(hostId, "json", ipAddress);
        return exportData;
    }

    @Override
    public AssetExportFileDTO exportExcel(Long hostId, String ipAddress) {
        licenseGuard.requireFeature(LicenseFeature.ASSET);
        AssetExportDTO exportData = buildExportData(hostId);
        byte[] content = buildExcel(exportData);
        recordExport(hostId, "excel", ipAddress);
        String fileName = "asset_export_" + hostId + "_" + FILE_TIME_FORMATTER.format(LocalDateTime.now()) + ".xlsx";
        return new AssetExportFileDTO(fileName, content);
    }

    private AssetExportDTO buildExportData(Long hostId) {
        Long tenantId = currentTenantId();
        HostEntity host = hostMapper.selectByIdAndTenant(hostId, tenantId);
        if (host == null) {
            throw new ResourceNotFoundException("主机不存在 id=" + hostId);
        }
        if (!StringUtils.hasText(host.getMacAddress())) {
            throw new IllegalArgumentException("主机未绑定MAC地址，无法导出资产清单");
        }

        ProcessEntity processRecord = processMapper.selectLatestByMacAndTenant(host.getMacAddress(), tenantId);
        ServiceEntity serviceRecord = serviceMapper.selectLatestByMacAndTenant(host.getMacAddress(), tenantId);
        AppEntity appRecord = appMapper.selectLatestByMacAndTenant(host.getMacAddress(), tenantId);

        List<Map<String, Object>> processes = parseAssetJson(processRecord == null ? null : processRecord.getAssetJson());
        List<Map<String, Object>> services = parseAssetJson(serviceRecord == null ? null : serviceRecord.getAssetJson());
        List<Map<String, Object>> applications = parseAssetJson(appRecord == null ? null : appRecord.getAssetJson());

        AssetExportDTO dto = new AssetExportDTO();
        dto.setHost(toHostResponse(host));
        dto.setProcesses(processes);
        dto.setServices(services);
        dto.setApplications(applications);
        dto.setAiRiskSummary(buildRiskSummary(processes, services, applications));
        return dto;
    }

    private List<Map<String, Object>> parseAssetJson(String assetJson) {
        if (!StringUtils.hasText(assetJson)) {
            return List.of();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(assetJson);
            if (!node.isArray()) {
                return List.of();
            }
            return OBJECT_MAPPER.convertValue(node, LIST_OF_MAPS);
        } catch (Exception e) {
            throw new IllegalArgumentException("资产JSON格式异常，无法导出");
        }
    }

    @SafeVarargs
    private Map<String, Object> buildRiskSummary(List<Map<String, Object>>... assetGroups) {
        Map<String, Object> summary = new LinkedHashMap<>();
        Map<String, Integer> levelCounts = new LinkedHashMap<>();
        for (String level : RISK_LEVELS) {
            levelCounts.put(level, 0);
        }

        int totalItems = 0;
        int analyzedItems = 0;
        int riskItems = 0;
        List<Map<String, Object>> highRiskItems = new ArrayList<>();

        for (List<Map<String, Object>> group : assetGroups) {
            for (Map<String, Object> item : group) {
                totalItems++;
                String riskLevel = stringValue(item.get("risk_level"));
                if (!StringUtils.hasText(riskLevel)) {
                    continue;
                }
                analyzedItems++;
                String normalizedLevel = riskLevel.toUpperCase();
                levelCounts.put(normalizedLevel, levelCounts.getOrDefault(normalizedLevel, 0) + 1);
                if (!"NONE".equals(normalizedLevel)) {
                    riskItems++;
                }
                if ("HIGH".equals(normalizedLevel)) {
                    highRiskItems.add(compactRiskItem(item));
                }
            }
        }

        summary.put("exists", analyzedItems > 0);
        summary.put("totalItems", totalItems);
        summary.put("analyzedItems", analyzedItems);
        summary.put("riskItems", riskItems);
        summary.put("levelCounts", levelCounts);
        summary.put("highRiskItems", highRiskItems);
        return summary;
    }

    private Map<String, Object> compactRiskItem(Map<String, Object> item) {
        Map<String, Object> compact = new LinkedHashMap<>();
        putIfPresent(compact, "name", item.get("name"));
        putIfPresent(compact, "displayName", item.get("displayName"));
        putIfPresent(compact, "pid", item.get("pid"));
        putIfPresent(compact, "risk_score", item.get("risk_score"));
        putIfPresent(compact, "risk_tags", item.get("risk_tags"));
        putIfPresent(compact, "result", item.get("result"));
        return compact;
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            target.put(key, value);
        }
    }

    private byte[] buildExcel(AssetExportDTO data) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            writeHostSheet(workbook, headerStyle, data.getHost());
            writeMapListSheet(workbook, "Processes", headerStyle, data.getProcesses());
            writeMapListSheet(workbook, "Services", headerStyle, data.getServices());
            writeMapListSheet(workbook, "Applications", headerStyle, data.getApplications());
            writeSummarySheet(workbook, headerStyle, data.getAiRiskSummary());

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Excel导出失败: " + e.getMessage(), e);
        }
    }

    private void writeHostSheet(Workbook workbook, CellStyle headerStyle, HostResponseDTO host) {
        Sheet sheet = workbook.createSheet("HostInfo");
        Row header = sheet.createRow(0);
        writeCell(header, 0, "Field", headerStyle);
        writeCell(header, 1, "Value", headerStyle);

        Map<String, Object> fields = hostFields(host);
        int rowIndex = 1;
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            Row row = sheet.createRow(rowIndex++);
            writeCell(row, 0, entry.getKey(), null);
            writeCell(row, 1, entry.getValue(), null);
        }
        autoSize(sheet, 2);
    }

    private Map<String, Object> hostFields(HostResponseDTO host) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", host.getId());
        fields.put("hostname", host.getHostname());
        fields.put("ipv4", host.getIpv4());
        fields.put("macAddress", host.getMacAddress());
        fields.put("osName", host.getOsName());
        fields.put("osVersion", host.getOsVersion());
        fields.put("osArch", host.getOsArch());
        fields.put("osRelease", host.getOsRelease());
        fields.put("cpuModel", host.getCpuModel());
        fields.put("cpuPhysicalCores", host.getCpuPhysicalCores());
        fields.put("cpuLogicalCores", host.getCpuLogicalCores());
        fields.put("memTotal", host.getMemTotal());
        fields.put("memUsed", host.getMemUsed());
        fields.put("memAvailable", host.getMemAvailable());
        fields.put("memUsage", host.getMemUsage());
        fields.put("status", host.getStatus());
        fields.put("createdAt", host.getCreatedAt());
        fields.put("updatedAt", host.getUpdatedAt());
        return fields;
    }

    private void writeMapListSheet(Workbook workbook, String sheetName, CellStyle headerStyle, List<Map<String, Object>> rows) {
        Sheet sheet = workbook.createSheet(sheetName);
        List<String> headers = collectHeaders(rows);
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.size(); i++) {
            writeCell(header, i, headers.get(i), headerStyle);
        }
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex + 1);
            Map<String, Object> item = rows.get(rowIndex);
            for (int colIndex = 0; colIndex < headers.size(); colIndex++) {
                writeCell(row, colIndex, item.get(headers.get(colIndex)), null);
            }
        }
        autoSize(sheet, headers.size());
    }

    private List<String> collectHeaders(List<Map<String, Object>> rows) {
        List<String> headers = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            for (String key : row.keySet()) {
                if (!headers.contains(key)) {
                    headers.add(key);
                }
            }
        }
        if (headers.isEmpty()) {
            headers.add("NoData");
        }
        return headers;
    }

    private void writeSummarySheet(Workbook workbook, CellStyle headerStyle, Map<String, Object> summary) {
        Sheet sheet = workbook.createSheet("AIRiskSummary");
        Row header = sheet.createRow(0);
        writeCell(header, 0, "Metric", headerStyle);
        writeCell(header, 1, "Value", headerStyle);

        int rowIndex = 1;
        for (Map.Entry<String, Object> entry : summary.entrySet()) {
            Row row = sheet.createRow(rowIndex++);
            writeCell(row, 0, entry.getKey(), null);
            writeCell(row, 1, entry.getValue(), null);
        }
        autoSize(sheet, 2);
    }

    private void writeCell(Row row, int index, Object value, CellStyle style) {
        Cell cell = row.createCell(index);
        if (style != null) {
            cell.setCellStyle(style);
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
        } else {
            cell.setCellValue(value == null ? "" : stringify(value));
        }
    }

    private String stringify(Object value) {
        if (value instanceof String text) {
            return text;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private void autoSize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i);
            int currentWidth = sheet.getColumnWidth(i);
            sheet.setColumnWidth(i, Math.min(Math.max(currentWidth, 3000), 12000));
        }
    }

    private void recordExport(Long hostId, String format, String ipAddress) {
        AssetExportLogEntity log = new AssetExportLogEntity();
        log.setTenantId(currentTenantId());
        log.setUserId(SecurityUtils.getCurrentUserId());
        log.setHostId(hostId);
        log.setExportTime(LocalDateTime.now());
        log.setExportFormat(format);
        log.setIpAddress(ipAddress);
        assetExportLogMapper.insert(log);
    }

    private HostResponseDTO toHostResponse(HostEntity entity) {
        HostResponseDTO dto = new HostResponseDTO();
        dto.setId(entity.getId());
        dto.setHostname(entity.getHostname());
        dto.setIpv4(entity.getIpv4());
        dto.setMacAddress(entity.getMacAddress());
        dto.setOsName(entity.getOsName());
        dto.setOsVersion(entity.getOsVersion());
        dto.setOsArch(entity.getOsArch());
        dto.setOsRelease(entity.getOsRelease());
        dto.setCpuModel(entity.getCpuModel());
        dto.setCpuPhysicalCores(entity.getCpuPhysicalCores());
        dto.setCpuLogicalCores(entity.getCpuLogicalCores());
        dto.setMemTotal(entity.getMemTotal());
        dto.setMemUsed(entity.getMemUsed());
        dto.setMemAvailable(entity.getMemAvailable());
        dto.setMemUsage(entity.getMemUsage());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }
}
