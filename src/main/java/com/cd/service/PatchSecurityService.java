package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.PatchRiskDetailDTO;
import com.cd.dto.PatchRiskHostDTO;
import com.cd.dto.PatchSecurityActionResultDTO;
import com.cd.dto.PatchSecuritySummaryDTO;
import com.cd.entity.HostPatchRiskEntity;

import java.util.List;

public interface PatchSecurityService {

    PatchSecuritySummaryDTO summary();

    PageResult<PatchRiskHostDTO> listRiskHosts(int page,
                                               int size,
                                               String keyword,
                                               String riskLevel,
                                               String riskType,
                                               Integer pendingReboot,
                                               String osName);

    List<PatchRiskDetailDTO> listHostRisks(Long hostId);

    PatchSecurityActionResultDTO analyze(List<Long> hostIds);

    PatchSecurityActionResultDTO scan(List<Long> hostIds);

    List<HostPatchRiskEntity> analyzeHost(Long hostId);
}
