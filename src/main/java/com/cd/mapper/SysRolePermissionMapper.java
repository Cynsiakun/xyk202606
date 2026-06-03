package com.cd.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SysRolePermissionMapper {

    int deleteByRoleId(@Param("roleId") Long roleId);

    int insertBatch(@Param("roleId") Long roleId, @Param("permissionIds") List<Long> permissionIds);

    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);

    long countByPermissionId(@Param("permissionId") Long permissionId);
}
