package com.cd.mapper;

import com.cd.entity.HostVulnResultEntity;
import com.cd.dto.VulnDetectionSummaryDTO;
import com.cd.dto.VulnHostOverviewDTO;
import com.cd.dto.VulnVerificationRuleDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface HostVulnResultMapper {

    int insert(HostVulnResultEntity entity);

    int markInactiveByHostId(@Param("hostId") Long hostId);

    List<HostVulnResultEntity> selectActiveByHostId(@Param("hostId") Long hostId);

    List<VulnVerificationRuleDTO> selectPendingVerificationRules(@Param("hostId") Long hostId);

    int updateVerifyStatusByIds(@Param("ids") List<Long> ids,
                                @Param("verifyStatus") String verifyStatus);

    int updateTaskIdByIds(@Param("ids") List<Long> ids,
                          @Param("taskId") Long taskId);

    int updateVerifyStatusByTaskAndRule(@Param("taskId") Long taskId,
                                        @Param("ruleId") Long ruleId,
                                        @Param("verifyStatus") String verifyStatus);

    VulnDetectionSummaryDTO selectSummary();

    List<VulnHostOverviewDTO> selectHostOverviews();
}
