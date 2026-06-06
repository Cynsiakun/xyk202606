package com.cd.mapper;

import com.cd.entity.ServiceEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 资产探测 — 服务数据持久化。
 */
public interface ServiceMapper {

    int insert(ServiceEntity entity);

    ServiceEntity selectById(@Param("id") Long id);

    List<ServiceEntity> selectPage(@Param("offset") int offset,
                                   @Param("size") int size,
                                   @Param("keyword") String keyword,
                                   @Param("macAddress") String macAddress);

    long countFiltered(@Param("keyword") String keyword,
                       @Param("macAddress") String macAddress);

    int softDeleteById(@Param("id") Long id);

    int updateAssetJsonById(@Param("id") Long id,
                            @Param("assetJson") String assetJson,
                            @Param("assetCount") Integer assetCount);

    List<ServiceEntity> selectLatestPerMac();

    ServiceEntity selectLatestByMac(@Param("macAddress") String macAddress);
}
