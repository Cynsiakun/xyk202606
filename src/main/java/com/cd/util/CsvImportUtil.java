package com.cd.util;

import com.cd.dto.CsvImportResultDTO;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class CsvImportUtil {

    private CsvImportUtil() {
    }

    public static <T> CsvImportResultDTO importCsv(MultipartFile file,
                                                   Function<CSVRecord, T> rowMapper,
                                                   BiConsumer<T, CsvImportResultDTO> rowConsumer) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (rowMapper == null || rowConsumer == null) {
            throw new IllegalArgumentException("CSV导入处理器不能为空");
        }

        CsvImportResultDTO result = new CsvImportResultDTO();
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {
            for (CSVRecord record : parser) {
                try {
                    T row = rowMapper.apply(record);
                    rowConsumer.accept(row, result);
                } catch (Exception ex) {
                    result.addFailure(buildRowError(record, ex));
                }
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("CSV读取失败: " + ex.getMessage(), ex);
        }
        return result;
    }

    public static String getValue(CSVRecord record, String... headerNames) {
        if (record == null || headerNames == null) {
            return "";
        }
        for (String headerName : headerNames) {
            if (headerName == null || headerName.isBlank()) {
                continue;
            }
            String actualHeader = findHeader(record, headerName);
            if (actualHeader != null && record.isMapped(actualHeader)) {
                String value = record.get(actualHeader);
                return value == null ? "" : value.trim();
            }
        }
        return "";
    }

    private static String findHeader(CSVRecord record, String headerName) {
        for (String candidate : record.toMap().keySet()) {
            if (candidate != null && candidate.trim().toLowerCase(Locale.ROOT)
                    .equals(headerName.trim().toLowerCase(Locale.ROOT))) {
                return candidate;
            }
        }
        return null;
    }

    private static String buildRowError(CSVRecord record, Exception ex) {
        long rowNumber = record == null ? -1 : record.getRecordNumber() + 1;
        String message = ex == null || ex.getMessage() == null || ex.getMessage().isBlank()
                ? "未知错误"
                : ex.getMessage();
        if (rowNumber > 0) {
            return "第" + rowNumber + "行导入失败: " + message;
        }
        return "导入失败: " + message;
    }
}
