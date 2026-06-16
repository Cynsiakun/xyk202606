package com.cd.mapper;

import com.cd.entity.HostPatchStatusEntity;
import org.apache.ibatis.annotations.Param;

public interface HostPatchStatusMapper {

    int insert(HostPatchStatusEntity entity);

    int updateById(HostPatchStatusEntity entity);

    HostPatchStatusEntity selectLatestByHostId(@Param("hostId") Long hostId);

    HostPatchStatusEntity selectLatestByHostIdAndTenant(@Param("hostId") Long hostId,
                                                        @Param("tenantId") Long tenantId);

    int deleteByHostIdAndExcludeId(@Param("hostId") Long hostId, @Param("excludeId") Long excludeId);

    int deleteByHostIdAndTenantExcludeId(@Param("hostId") Long hostId,
                                         @Param("tenantId") Long tenantId,
                                         @Param("excludeId") Long excludeId);
}
