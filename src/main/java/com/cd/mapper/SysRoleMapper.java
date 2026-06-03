package com.cd.mapper;

import com.cd.entity.SysRoleEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SysRoleMapper {

    int insert(SysRoleEntity entity);

    int updateById(SysRoleEntity entity);

    int deleteById(@Param("id") Long id);

    SysRoleEntity selectById(@Param("id") Long id);

    SysRoleEntity selectByRoleCode(@Param("roleCode") String roleCode);

    List<SysRoleEntity> selectPage(@Param("offset") int offset,
                                   @Param("size") int size,
                                   @Param("keyword") String keyword);

    long countAll(@Param("keyword") String keyword);

    List<SysRoleEntity> selectAll();
}
