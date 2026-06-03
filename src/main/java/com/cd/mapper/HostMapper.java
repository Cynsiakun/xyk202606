package com.cd.mapper;

import com.cd.entity.HostEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface HostMapper {

    int insert(HostEntity entity);

    int updateById(HostEntity entity);

    int deleteById(@Param("id") Long id);

    HostEntity selectById(@Param("id") Long id);

    HostEntity selectByMac(@Param("macAddress") String macAddress);

    /**
     * 按 MAC 唯一键做存在即更新、不存在即插入。
     */
    int upsertByMac(HostEntity entity);

    List<HostEntity> selectPage(@Param("offset") int offset,
                                @Param("size") int size,
                                @Param("keyword") String keyword);

    long countAll(@Param("keyword") String keyword);
}
