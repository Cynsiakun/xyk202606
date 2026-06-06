package com.cd.mapper;

import com.cd.entity.AppEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 资产探测 — 软件数据持久化。
 */
public interface AppMapper {

    int insert(AppEntity entity);

    AppEntity selectById(@Param("id") Long id);

    List<AppEntity> selectPage(@Param("offset") int offset,
                               @Param("size") int size,
                               @Param("keyword") String keyword,
                               @Param("macAddress") String macAddress);

    long countFiltered(@Param("keyword") String keyword,
                       @Param("macAddress") String macAddress);

    int softDeleteById(@Param("id") Long id);

    int updateAssetJsonById(@Param("id") Long id,
                            @Param("assetJson") String assetJson,
                            @Param("assetCount") Integer assetCount);

    List<AppEntity> selectLatestPerMac();

    AppEntity selectLatestByMac(@Param("macAddress") String macAddress);
}
