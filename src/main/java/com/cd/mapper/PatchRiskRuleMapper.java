package com.cd.mapper;

import com.cd.dto.RiskContext;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PatchRiskRuleMapper {

    List<RiskContext> selectMissingPatchRisks(@Param("hostId") Long hostId);

    List<RiskContext> selectKnownIssueRisks(@Param("hostId") Long hostId);

    List<RiskContext> selectPendingRebootRisks(@Param("hostId") Long hostId);

    List<RiskContext> selectInstallFailureRisks(@Param("hostId") Long hostId);

    List<RiskContext> selectEolRisks(@Param("hostId") Long hostId);
}
