package com.cd.service;

import com.cd.dto.VulnVerificationTaskResponseDTO;

import java.util.List;
import java.util.Map;

public interface VulnVerificationService {

    VulnVerificationTaskResponseDTO verifyHost(Long hostId);

    Map<Long, Long> batchVerify(List<Long> hostIds);

    VulnVerificationTaskResponseDTO retry(Long taskId);
}
