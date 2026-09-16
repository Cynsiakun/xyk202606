package com.cd.mapper;

import com.cd.entity.HostVulnResultEntity;
import com.cd.dto.VulnDetectionSummaryDTO;
import com.cd.dto.VulnAffectedHostDTO;
import com.cd.dto.VulnHostOverviewDTO;
import com.cd.dto.VulnOverviewDTO;
import com.cd.dto.VulnVerificationRuleDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface HostVulnResultMapper {

    int insert(HostVulnResultEntity entity);

    int markInactiveByHostId(@Param("hostId") Long hostId);

    int markInactiveByHostIdAndTenant(@Param("hostId") Long hostId,
                                      @Param("tenantId") Long tenantId);

    int countActiveByHostAndVerifyStatusAndTenant(@Param("hostId") Long hostId,
                                                  @Param("verifyStatus") String verifyStatus,
                                                  @Param("tenantId") Long tenantId);

    List<HostVulnResultEntity> selectActiveByHostId(@Param("hostId") Long hostId);

    List<HostVulnResultEntity> selectActiveByHostIdAndTenant(@Param("hostId") Long hostId,
                                                             @Param("tenantId") Long tenantId);

    List<VulnVerificationRuleDTO> selectPendingVerificationRules(@Param("hostId") Long hostId);

    List<VulnVerificationRuleDTO> selectPendingVerificationRulesByTenant(@Param("hostId") Long hostId,
                                                                         @Param("tenantId") Long tenantId);

    VulnVerificationRuleDTO selectVerificationRuleByResultId(@Param("hostId") Long hostId,
                                                             @Param("resultId") Long resultId);

    VulnVerificationRuleDTO selectVerificationRuleByResultIdAndTenant(@Param("hostId") Long hostId,
                                                                      @Param("resultId") Long resultId,
                                                                      @Param("tenantId") Long tenantId);

    List<VulnVerificationRuleDTO> selectVerificationRulesByResultIds(@Param("ids") List<Long> ids);

    List<VulnVerificationRuleDTO> selectVerificationRulesByResultIdsAndTenant(@Param("ids") List<Long> ids,
                                                                              @Param("tenantId") Long tenantId);

    int updateVerifyStatusByIds(@Param("ids") List<Long> ids,
                                @Param("verifyStatus") String verifyStatus);

    int updateVerifyStatusByIdsAndTenant(@Param("ids") List<Long> ids,
                                         @Param("verifyStatus") String verifyStatus,
                                         @Param("tenantId") Long tenantId);

    int updateTaskIdByIds(@Param("ids") List<Long> ids,
                          @Param("taskId") Long taskId);

    int updateTaskIdByIdsAndTenant(@Param("ids") List<Long> ids,
                                   @Param("taskId") Long taskId,
                                   @Param("tenantId") Long tenantId);

    int updateVerifyStatusByTaskAndRule(@Param("taskId") Long taskId,
                                        @Param("ruleId") Long ruleId,
                                        @Param("verifyStatus") String verifyStatus);

    int updateVerifyStatusByTaskAndRuleAndTenant(@Param("taskId") Long taskId,
                                                 @Param("ruleId") Long ruleId,
                                                 @Param("verifyStatus") String verifyStatus,
                                                 @Param("tenantId") Long tenantId);

    VulnDetectionSummaryDTO selectSummary();

    VulnDetectionSummaryDTO selectSummaryByTenant(@Param("tenantId") Long tenantId);

    List<VulnHostOverviewDTO> selectHostOverviews();

    List<VulnHostOverviewDTO> selectHostOverviewsByTenant(@Param("tenantId") Long tenantId);

    List<VulnOverviewDTO> selectVulnOverviews();

    List<VulnOverviewDTO> selectVulnOverviewsByTenant(@Param("tenantId") Long tenantId);

    List<VulnAffectedHostDTO> selectAffectedHostsByRuleId(@Param("ruleId") Long ruleId);

    List<VulnAffectedHostDTO> selectAffectedHostsByRuleIdAndTenant(@Param("ruleId") Long ruleId,
                                                                   @Param("tenantId") Long tenantId);

    List<Long> selectGroupedResultIdsByResultIdsAndTenant(@Param("ids") List<Long> ids,
                                                          @Param("tenantId") Long tenantId);

    int ignoreByResultIds(@Param("ids") List<Long> ids);

    int ignoreByResultIdsAndTenant(@Param("ids") List<Long> ids,
                                    @Param("tenantId") Long tenantId);

    int fixByHostIdAndTenant(@Param("hostId") Long hostId,
                             @Param("tenantId") Long tenantId);

    int fixByRuleIdAndTenant(@Param("ruleId") Long ruleId,
                             @Param("tenantId") Long tenantId);

    int completeRepairingByTenant(@Param("tenantId") Long tenantId,
                                  @Param("thresholdSeconds") int thresholdSeconds);

    int resetVerifyingTimeoutByTenant(@Param("tenantId") Long tenantId,
                                      @Param("thresholdSeconds") int thresholdSeconds,
                                      @Param("fromStatus") String fromStatus,
                                      @Param("toStatus") String toStatus);
}
