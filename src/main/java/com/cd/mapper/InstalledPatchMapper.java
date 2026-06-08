package com.cd.mapper;

import com.cd.entity.InstalledPatchEntity;
import org.apache.ibatis.annotations.Param;

public interface InstalledPatchMapper {

    int insert(InstalledPatchEntity entity);

    int updateById(InstalledPatchEntity entity);

    InstalledPatchEntity selectLatestByHostIdAndPatchId(@Param("hostId") Long hostId,
                                                        @Param("patchId") String patchId);

    int deleteByHostIdAndPatchIdExcludeId(@Param("hostId") Long hostId,
                                          @Param("patchId") String patchId,
                                          @Param("excludeId") Long excludeId);
}
