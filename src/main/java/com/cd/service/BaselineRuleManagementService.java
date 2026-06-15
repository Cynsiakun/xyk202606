package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.BaselineRuleManageRequestDTO;
import com.cd.entity.BaselineRuleEntity;

public interface BaselineRuleManagementService {

    PageResult<BaselineRuleEntity> list(Integer page, Integer size, String keyword,
                                        String category, String severity, String status, Integer enabled);

    BaselineRuleEntity detail(Long id);

    BaselineRuleEntity create(BaselineRuleManageRequestDTO request);

    BaselineRuleEntity update(Long id, BaselineRuleManageRequestDTO request);

    void archive(Long id);

    BaselineRuleEntity setEnabled(Long id, Integer enabled);
}
