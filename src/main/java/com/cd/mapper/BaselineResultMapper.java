package com.cd.mapper;

import com.cd.entity.BaselineResultEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BaselineResultMapper {

    int insert(BaselineResultEntity entity);

    /** 统计某 task_host 已生成的 result 数量，用于幂等判断。 */
    int countByTaskHostId(@Param("taskHostId") Long taskHostId);

    /** 按主键批量查询结果，供修复/工单按 resultId 反查主机与规则。 */
    List<BaselineResultEntity> selectByIds(@Param("ids") List<Long> ids);

    List<BaselineResultEntity> selectByIdsAndTenant(@Param("ids") List<Long> ids,
                                                    @Param("tenantId") Long tenantId);

    BaselineResultEntity selectLatestBefore(@Param("hostId") Long hostId,
                                            @Param("ruleId") Long ruleId,
                                            @Param("checkKey") String checkKey,
                                            @Param("beforeId") Long beforeId);

    BaselineResultEntity selectLatestBeforeByTenant(@Param("hostId") Long hostId,
                                                    @Param("ruleId") Long ruleId,
                                                    @Param("checkKey") String checkKey,
                                                    @Param("beforeId") Long beforeId,
                                                    @Param("tenantId") Long tenantId);

    /** 更新单条结果的修复状态（NONE/IN_PROGRESS/FIXED/FAILED/ROLLED_BACK/TICKETED）。 */
    int updateRemediationStatus(@Param("id") Long id,
                                @Param("remediationStatus") String remediationStatus);

    int updateRemediationStatusByTenant(@Param("id") Long id,
                                        @Param("remediationStatus") String remediationStatus,
                                        @Param("tenantId") Long tenantId);

    /** 批量更新结果的修复状态。 */
    int updateRemediationStatusByIds(@Param("ids") List<Long> ids,
                                     @Param("remediationStatus") String remediationStatus);

    int updateRemediationStatusByIdsAndTenant(@Param("ids") List<Long> ids,
                                             @Param("remediationStatus") String remediationStatus,
                                             @Param("tenantId") Long tenantId);
}
