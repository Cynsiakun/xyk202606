package com.cd.mapper;

import com.cd.entity.SysMenuEntity;

import java.util.List;

public interface SysMenuMapper {

    List<SysMenuEntity> selectAllEnabled();

    List<SysMenuEntity> selectEnabledByUserId(Long userId);
}
