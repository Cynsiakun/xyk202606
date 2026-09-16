package com.cd.mapper;

import com.cd.entity.InstalledPatchEntity;
import org.apache.ibatis.annotations.Param;

public interface InstalledPatchMapper {

    int insert(InstalledPatchEntity entity);

    int updateById(InstalledPatchEntity entity);

    InstalledPatchEntity selectLatestByHostIdAndPatchId(@Param("hostId") Long hostId,
                                                        @Param("patchId") String patchId);

    InstalledPatchEntity selectLatestByHostIdAndPatchIdAndTenant(@Param("hostId") Long hostId,
                                                                 @Param("patchId") String patchId,
                                                                 @Param("tenantId") Long tenantId);

    int deleteByHostIdAndPatchIdExcludeId(@Param("hostId") Long hostId,
                                          @Param("patchId") String patchId,
                                          @Param("excludeId") Long excludeId);

    int deleteByHostIdAndPatchIdAndTenantExcludeId(@Param("hostId") Long hostId,
                                                   @Param("patchId") String patchId,
                                                   @Param("tenantId") Long tenantId,
                                                   @Param("excludeId") Long excludeId);
}
