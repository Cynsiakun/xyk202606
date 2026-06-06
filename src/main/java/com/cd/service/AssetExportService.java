package com.cd.service;

import com.cd.dto.AssetExportDTO;
import com.cd.dto.AssetExportFileDTO;

public interface AssetExportService {

    AssetExportDTO exportJson(Long hostId, String ipAddress);

    AssetExportFileDTO exportExcel(Long hostId, String ipAddress);
}
