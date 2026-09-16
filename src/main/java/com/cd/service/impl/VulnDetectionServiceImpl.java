package com.cd.service.impl;

import com.cd.dto.VulnDetectionSummaryDTO;
import com.cd.dto.VulnAffectedHostDTO;
import com.cd.dto.VulnHostOverviewDTO;
import com.cd.dto.VulnOverviewDTO;
import com.cd.dto.PatchSecurityActionResultDTO;
import com.cd.common.security.TenantContextHolder;
import com.cd.entity.HostVulnResultEntity;
import com.cd.mapper.HostMapper;
import com.cd.mapper.HostVulnResultMapper;
import com.cd.service.VulnDetectionService;
import com.cd.service.VulnRuleEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VulnDetectionServiceImpl implements VulnDetectionService {

    private final VulnRuleEngine vulnRuleEngine;
    private final HostVulnResultMapper hostVulnResultMapper;
    private final HostMapper hostMapper;

    @Override
    public VulnDetectionSummaryDTO summary() {
        VulnDetectionSummaryDTO summary = hostVulnResultMapper.selectSummaryByTenant(currentTenantId());
        return summary == null ? new VulnDetectionSummaryDTO() : summary;
    }

    @Override
    public List<VulnHostOverviewDTO> listHostOverviews() {
        return hostVulnResultMapper.selectHostOverviewsByTenant(currentTenantId());
    }

    @Override
    public List<VulnOverviewDTO> listVulnOverviews() {
        return hostVulnResultMapper.selectVulnOverviewsByTenant(currentTenantId());
    }

    @Override
    public List<VulnAffectedHostDTO> listAffectedHosts(Long ruleId) {
        if (ruleId == null) {
            return List.of();
        }
        return hostVulnResultMapper.selectAffectedHostsByRuleIdAndTenant(ruleId, currentTenantId());
    }

    @Override
    public int ignoreResults(List<Long> resultIds) {
        if (resultIds == null || resultIds.isEmpty()) {
            return 0;
        }
        List<Long> seedIds = resultIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (seedIds.isEmpty()) {
            return 0;
        }
        List<Long> ids = hostVulnResultMapper.selectGroupedResultIdsByResultIdsAndTenant(seedIds, currentTenantId());
        return ids.isEmpty() ? 0 : hostVulnResultMapper.ignoreByResultIdsAndTenant(ids, currentTenantId());
    }

    @Override
    public PatchSecurityActionResultDTO evaluateAllHosts() {
        List<Long> hostIds = hostMapper.selectAllIdsByTenant(currentTenantId());
        PatchSecurityActionResultDTO result = new PatchSecurityActionResultDTO();
        result.setTotal(hostIds.size());
        for (Long hostId : hostIds) {
            try {
                vulnRuleEngine.evaluateHost(hostId);
                result.setSuccess(result.getSuccess() + 1);
            } catch (Exception e) {
                result.setFailed(result.getFailed() + 1);
            }
        }
        return result;
    }

    @Override
    public List<HostVulnResultEntity> evaluateHost(Long hostId) {
        return vulnRuleEngine.evaluateHost(hostId);
    }

    @Override
    public List<HostVulnResultEntity> listActiveResults(Long hostId) {
        if (hostId == null) {
            return List.of();
        }
        return hostVulnResultMapper.selectActiveByHostIdAndTenant(hostId, currentTenantId());
    }

    @Override
    public int fixHost(Long hostId) {
        if (hostId == null || hostId <= 0) {
            return 0;
        }
        return hostVulnResultMapper.fixByHostIdAndTenant(hostId, currentTenantId());
    }

    @Override
    public int fixByRuleId(Long ruleId) {
        if (ruleId == null || ruleId <= 0) {
            return 0;
        }
        return hostVulnResultMapper.fixByRuleIdAndTenant(ruleId, currentTenantId());
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }

}
