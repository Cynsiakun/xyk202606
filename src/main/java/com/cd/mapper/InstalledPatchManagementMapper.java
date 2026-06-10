package com.cd.mapper;

import com.cd.entity.InstalledPatchEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface InstalledPatchManagementMapper {

    int insert(InstalledPatchEntity entity);

    int updateById(InstalledPatchEntity entity);

    int deleteById(@Param("id") Long id);

    int deleteBatch(@Param("ids") List<Long> ids);

    InstalledPatchEntity selectById(@Param("id") Long id);

    List<InstalledPatchEntity> selectPage(@Param("offset") int offset,
                                          @Param("size") int size,
                                          @Param("keyword") String keyword,
                                          @Param("installStatus") String installStatus);

    long countAll(@Param("keyword") String keyword,
                  @Param("installStatus") String installStatus);
}
