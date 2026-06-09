package com.cd.service;

import com.cd.entity.HostVulnResultEntity;

import java.util.List;

public interface VulnDetectionService {

    List<HostVulnResultEntity> evaluateHost(Long hostId);

    List<HostVulnResultEntity> listActiveResults(Long hostId);
}
