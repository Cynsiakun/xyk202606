package com.cd.mapper;

import com.cd.entity.PatchCveMapEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface PatchCveMapMapper {

    int insert(PatchCveMapEntity entity);

    int updateById(PatchCveMapEntity entity);

    int deleteById(@Param("id") Long id);

    int deleteBatch(@Param("ids") List<Long> ids);

    PatchCveMapEntity selectById(@Param("id") Long id);

    PatchCveMapEntity selectByPatchIdAndCveId(@Param("patchId") String patchId,
                                              @Param("cveId") String cveId);

    List<PatchCveMapEntity> selectPage(@Param("offset") int offset,
                                       @Param("size") int size,
                                       @Param("keyword") String keyword,
                                       @Param("severity") String severity,
                                       @Param("kevFlag") Integer kevFlag);

    long countAll(@Param("keyword") String keyword,
                  @Param("severity") String severity,
                  @Param("kevFlag") Integer kevFlag);
}
