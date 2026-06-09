package com.cd.mapper;

import com.cd.dto.PatchRiskDetailDTO;
import com.cd.dto.PatchRiskHostDTO;
import com.cd.dto.PatchSecuritySummaryDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PatchSecurityMapper {

    PatchSecuritySummaryDTO selectSummary();

    List<PatchRiskHostDTO> selectRiskHostPage(@Param("offset") int offset,
                                              @Param("size") int size,
                                              @Param("keyword") String keyword,
                                              @Param("riskLevel") String riskLevel,
                                              @Param("riskType") String riskType,
                                              @Param("pendingReboot") Integer pendingReboot,
                                              @Param("osName") String osName);

    long countRiskHosts(@Param("keyword") String keyword,
                        @Param("riskLevel") String riskLevel,
                        @Param("riskType") String riskType,
                        @Param("pendingReboot") Integer pendingReboot,
                        @Param("osName") String osName);

    List<PatchRiskDetailDTO> selectRiskDetailsByHostId(@Param("hostId") Long hostId);

    List<Long> selectHostIdsWithPatchStatus();

    List<Long> selectOnlineHostIds();
}
