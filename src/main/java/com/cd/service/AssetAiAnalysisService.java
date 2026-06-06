package com.cd.service;

import com.cd.dto.AssetRecordDTO;

public interface AssetAiAnalysisService {

    AssetRecordDTO analyzeAccount(Long id, String assetJson);
}
