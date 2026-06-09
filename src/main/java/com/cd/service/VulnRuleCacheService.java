package com.cd.service;

import com.cd.entity.VulnRuleEntity;

import java.util.List;
import java.util.Map;

public interface VulnRuleCacheService {

    Map<String, List<VulnRuleEntity>> getRuleCache();

    List<VulnRuleEntity> getRulesByType(String productType);

    void refresh();
}
