package com.cd.service.impl;

import com.cd.dto.AssetInfoDTO;
import com.cd.entity.HostVulnResultEntity;
import com.cd.entity.VulnRuleEntity;
import com.cd.service.RuleEnrichmentService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

@Service
@Order
public class DefaultRuleEnrichmentService implements RuleEnrichmentService {

    @Override
    public HostVulnResultEntity enrich(HostVulnResultEntity result, VulnRuleEntity rule, AssetInfoDTO assetInfo) {
        return result;
    }
}
