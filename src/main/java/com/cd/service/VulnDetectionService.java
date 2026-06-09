package com.cd.service;

import com.cd.dto.VulnDetectionSummaryDTO;
import com.cd.dto.VulnHostOverviewDTO;
import com.cd.entity.HostVulnResultEntity;
import com.cd.dto.PatchSecurityActionResultDTO;

import java.util.List;

public interface VulnDetectionService {

    VulnDetectionSummaryDTO summary();

    List<VulnHostOverviewDTO> listHostOverviews();

    PatchSecurityActionResultDTO evaluateAllHosts();

    List<HostVulnResultEntity> evaluateHost(Long hostId);

    List<HostVulnResultEntity> listActiveResults(Long hostId);
}
