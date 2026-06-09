package com.cd.service;

import com.cd.dto.VulnVerificationTaskResponseDTO;

import java.util.List;
import java.util.Map;

public interface VulnVerificationService {

    VulnVerificationTaskResponseDTO verifyHost(Long hostId);

    VulnVerificationTaskResponseDTO verifyResult(Long hostId, Long resultId);

    Map<Long, Long> verifyResults(List<Long> resultIds);

    Map<Long, Long> batchVerify(List<Long> hostIds);

    VulnVerificationTaskResponseDTO retry(Long taskId);
}
