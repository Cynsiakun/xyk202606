package com.cd.service;

import com.cd.dto.AssetInfoDTO;
import com.cd.entity.HostVulnResultEntity;
import com.cd.entity.VulnRuleEntity;

/**
 * Extension point for future CVE database and patch intelligence enrichment.
 */
public interface RuleEnrichmentService {

    HostVulnResultEntity enrich(HostVulnResultEntity result, VulnRuleEntity rule, AssetInfoDTO assetInfo);
}
