package com.cd.mapper;

import com.cd.entity.SysPermissionEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SysPermissionMapper {

    int insert(SysPermissionEntity entity);

    int updateById(SysPermissionEntity entity);

    int deleteById(@Param("id") Long id);

    SysPermissionEntity selectById(@Param("id") Long id);

    SysPermissionEntity selectByPermissionCode(@Param("permissionCode") String permissionCode);

    List<SysPermissionEntity> selectPage(@Param("offset") int offset,
                                         @Param("size") int size,
                                         @Param("keyword") String keyword);

    long countAll(@Param("keyword") String keyword);

    List<SysPermissionEntity> selectAll();
}
