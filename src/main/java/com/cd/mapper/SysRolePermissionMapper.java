package com.cd.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SysRolePermissionMapper {

    int deleteByRoleId(@Param("roleId") Long roleId);

    int insertBatch(@Param("roleId") Long roleId, @Param("permissionIds") List<Long> permissionIds);

    int insertMissingFromRole(@Param("targetRoleId") Long targetRoleId,
                              @Param("sourceRoleCode") String sourceRoleCode);

    int insertMissing(@Param("roleId") Long roleId,
                      @Param("permissionCode") String permissionCode);

    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);

    long countByPermissionId(@Param("permissionId") Long permissionId);
}
