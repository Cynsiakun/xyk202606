package com.cd.service;

import com.cd.dto.AssetRecordDTO;

public interface AssetAiAnalysisService {

    AssetRecordDTO analyzeAccount(Long id, String assetJson);

    AssetRecordDTO analyzeService(Long id, String assetJson);

    AssetRecordDTO analyzeProcess(Long id, String assetJson);

    AssetRecordDTO analyzeApp(Long id, String assetJson);
}
