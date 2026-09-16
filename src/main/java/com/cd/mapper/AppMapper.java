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

    AppEntity selectByIdAndTenant(@Param("id") Long id, @Param("tenantId") Long tenantId);

    List<AppEntity> selectPage(@Param("offset") int offset,
                               @Param("size") int size,
                               @Param("keyword") String keyword,
                               @Param("macAddress") String macAddress);

    List<AppEntity> selectPageByTenant(@Param("offset") int offset,
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

    List<AppEntity> selectLatestPerMac();

    List<AppEntity> selectLatestPerMacByTenant(@Param("tenantId") Long tenantId);

    AppEntity selectLatestByMac(@Param("macAddress") String macAddress);

    AppEntity selectLatestByMacAndTenant(@Param("macAddress") String macAddress,
                                         @Param("tenantId") Long tenantId);

    AppEntity selectLatestNonEmptyByMacAndTenant(@Param("macAddress") String macAddress,
                                                 @Param("tenantId") Long tenantId);
}
