package com.cd.service.impl;

import com.cd.entity.HostVulnResultEntity;
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
