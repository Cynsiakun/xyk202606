package com.cd.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AssetExportDTO {

    private HostResponseDTO host;
    private List<Map<String, Object>> processes;
    private List<Map<String, Object>> services;
    private List<Map<String, Object>> applications;
    private Map<String, Object> aiRiskSummary;
}
