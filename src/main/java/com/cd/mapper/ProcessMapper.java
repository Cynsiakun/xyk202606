package com.cd.mapper;

import com.cd.entity.ProcessEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 资产探测 — 进程数据持久化。
 */
public interface ProcessMapper {

    int insert(ProcessEntity entity);

    ProcessEntity selectById(@Param("id") Long id);

    List<ProcessEntity> selectPage(@Param("offset") int offset,
                                   @Param("size") int size,
                                   @Param("keyword") String keyword,
                                   @Param("macAddress") String macAddress);

    long countFiltered(@Param("keyword") String keyword,
                       @Param("macAddress") String macAddress);

    int softDeleteById(@Param("id") Long id);

    List<ProcessEntity> selectLatestPerMac();
}
