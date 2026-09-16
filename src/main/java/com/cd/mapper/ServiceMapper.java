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

    ServiceEntity selectByIdAndTenant(@Param("id") Long id, @Param("tenantId") Long tenantId);

    List<ServiceEntity> selectPage(@Param("offset") int offset,
                                   @Param("size") int size,
                                   @Param("keyword") String keyword,
                                   @Param("macAddress") String macAddress);

    List<ServiceEntity> selectPageByTenant(@Param("offset") int offset,
                                           @Param("size") int size,
                                           @Param("keyword") String keyword,
                                           @Param("macAddress") String macAddress,
                                           @Param("tenantId") Long tenantId);

    long countFiltered(@Param("keyword") String keyword,
                       @Param("macAddress") String macAddress);

    long countFilteredByTenant(@Param("keyword") String keyword,
                               @Param("macAddress") String macAddress,
                               @Param("tenantId") Long tenantId);

    int softDeleteById(@Param("id") Long id);

    int softDeleteByIdAndTenant(@Param("id") Long id, @Param("tenantId") Long tenantId);

    int updateAssetJsonById(@Param("id") Long id,
                            @Param("assetJson") String assetJson,
                            @Param("assetCount") Integer assetCount);

    int updateAssetJsonByIdAndTenant(@Param("id") Long id,
                                     @Param("assetJson") String assetJson,
                                     @Param("assetCount") Integer assetCount,
                                     @Param("tenantId") Long tenantId);

    List<ServiceEntity> selectLatestPerMac();

    List<ServiceEntity> selectLatestPerMacByTenant(@Param("tenantId") Long tenantId);

    ServiceEntity selectLatestByMac(@Param("macAddress") String macAddress);

    ServiceEntity selectLatestByMacAndTenant(@Param("macAddress") String macAddress,
                                             @Param("tenantId") Long tenantId);

    ServiceEntity selectLatestNonEmptyByMacAndTenant(@Param("macAddress") String macAddress,
                                                     @Param("tenantId") Long tenantId);
}
