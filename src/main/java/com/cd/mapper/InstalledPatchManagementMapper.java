package com.cd.mapper;

import com.cd.entity.InstalledPatchEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface InstalledPatchManagementMapper {

    int insert(InstalledPatchEntity entity);

    int updateById(InstalledPatchEntity entity);

    int deleteById(@Param("id") Long id);

    int deleteByIdAndTenant(@Param("id") Long id, @Param("tenantId") Long tenantId);

    int deleteBatch(@Param("ids") List<Long> ids);

    int deleteBatchByTenant(@Param("ids") List<Long> ids, @Param("tenantId") Long tenantId);

    InstalledPatchEntity selectById(@Param("id") Long id);

    InstalledPatchEntity selectByIdAndTenant(@Param("id") Long id, @Param("tenantId") Long tenantId);

    List<InstalledPatchEntity> selectPage(@Param("offset") int offset,
                                          @Param("size") int size,
                                          @Param("keyword") String keyword,
                                          @Param("installStatus") String installStatus,
                                          @Param("tenantId") Long tenantId);

    long countAll(@Param("keyword") String keyword,
                  @Param("installStatus") String installStatus,
                  @Param("tenantId") Long tenantId);
}
