package com.cd.mapper;

import com.cd.entity.HostPatchRiskEntity;
import org.apache.ibatis.annotations.Param;

public interface HostPatchRiskMapper {

    int insert(HostPatchRiskEntity entity);

    int updateById(HostPatchRiskEntity entity);

    HostPatchRiskEntity selectByRiskId(@Param("riskId") String riskId);

    int markFixedByHostIdExceptRiskIds(@Param("hostId") Long hostId,
                                       @Param("riskIds") java.util.List<String> riskIds);
}
