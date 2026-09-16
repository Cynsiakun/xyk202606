package com.cd.service;

import com.cd.entity.HostVulnResultEntity;

import java.util.List;

public interface VulnRuleEngine {

    List<HostVulnResultEntity> evaluateHost(Long hostId);

    List<HostVulnResultEntity> evaluateHostForTenant(Long hostId, Long tenantId);
}
