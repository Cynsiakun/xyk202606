package com.cd.service.impl;

import com.cd.dto.VulnDetectionSummaryDTO;
import com.cd.dto.VulnHostOverviewDTO;
import com.cd.dto.PatchSecurityActionResultDTO;
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
        VulnDetectionSummaryDTO summary = hostVulnResultMapper.selectSummary();
        return summary == null ? new VulnDetectionSummaryDTO() : summary;
    }

    @Override
    public List<VulnHostOverviewDTO> listHostOverviews() {
        return hostVulnResultMapper.selectHostOverviews();
    }

    @Override
    public PatchSecurityActionResultDTO evaluateAllHosts() {
        List<Long> hostIds = hostMapper.selectAllIds();
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
        return hostVulnResultMapper.selectActiveByHostId(hostId);
    }

}
