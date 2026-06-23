package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.AssetFingerprintRuleManageRequestDTO;
import com.cd.entity.AssetFingerprintRuleEntity;

public interface AssetFingerprintRuleManagementService {

    PageResult<AssetFingerprintRuleEntity> list(Integer page, Integer size, String keyword,
                                                String category, Integer port, Integer enabled);

    AssetFingerprintRuleEntity detail(Long id);

    AssetFingerprintRuleEntity create(AssetFingerprintRuleManageRequestDTO request);

    AssetFingerprintRuleEntity update(Long id, AssetFingerprintRuleManageRequestDTO request);

    void delete(Long id);
}
