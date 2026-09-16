package com.cd.service;

import com.cd.dto.VulnDetectionSummaryDTO;
import com.cd.dto.VulnAffectedHostDTO;
import com.cd.dto.VulnHostOverviewDTO;
import com.cd.dto.VulnOverviewDTO;
import com.cd.entity.HostVulnResultEntity;
import com.cd.dto.PatchSecurityActionResultDTO;

import java.util.List;

public interface VulnDetectionService {

    VulnDetectionSummaryDTO summary();

    List<VulnHostOverviewDTO> listHostOverviews();

    List<VulnOverviewDTO> listVulnOverviews();

    List<VulnAffectedHostDTO> listAffectedHosts(Long ruleId);

    int ignoreResults(List<Long> resultIds);

    PatchSecurityActionResultDTO evaluateAllHosts();

    List<HostVulnResultEntity> evaluateHost(Long hostId);

    List<HostVulnResultEntity> listActiveResults(Long hostId);

    int fixHost(Long hostId);

    int fixByRuleId(Long ruleId);
}
