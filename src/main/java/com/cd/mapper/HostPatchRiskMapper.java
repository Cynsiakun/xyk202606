package com.cd.mapper;

import com.cd.entity.HostPatchRiskEntity;
import org.apache.ibatis.annotations.Param;

public interface HostPatchRiskMapper {

    int insert(HostPatchRiskEntity entity);

    int updateById(HostPatchRiskEntity entity);

    HostPatchRiskEntity selectByRiskId(@Param("riskId") String riskId);

    HostPatchRiskEntity selectByRiskIdAndTenant(@Param("riskId") String riskId,
                                                @Param("tenantId") Long tenantId);

    int markFixedByHostIdExceptRiskIds(@Param("hostId") Long hostId,
                                       @Param("riskIds") java.util.List<String> riskIds);

    int markFixedByHostIdExceptRiskIdsAndTenant(@Param("hostId") Long hostId,
                                                @Param("riskIds") java.util.List<String> riskIds,
                                                @Param("tenantId") Long tenantId);
}
