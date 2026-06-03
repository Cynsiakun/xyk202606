package com.cd.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface RbacMapper {

    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);

    List<String> selectAllPermissionCodes();
}
