package com.cd.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SysUserRoleMapper {

    int deleteByUserId(@Param("userId") Long userId);

    int insertBatch(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    int insertIgnore(@Param("userId") Long userId, @Param("roleId") Long roleId);

    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    long countByRoleId(@Param("roleId") Long roleId);
}
