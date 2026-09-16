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

    ProcessEntity selectByIdAndTenant(@Param("id") Long id, @Param("tenantId") Long tenantId);

    List<ProcessEntity> selectPage(@Param("offset") int offset,
                                   @Param("size") int size,
                                   @Param("keyword") String keyword,
                                   @Param("macAddress") String macAddress);

    List<ProcessEntity> selectPageByTenant(@Param("offset") int offset,
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

    List<ProcessEntity> selectLatestPerMac();

    List<ProcessEntity> selectLatestPerMacByTenant(@Param("tenantId") Long tenantId);

    ProcessEntity selectLatestByMac(@Param("macAddress") String macAddress);

    ProcessEntity selectLatestByMacAndTenant(@Param("macAddress") String macAddress,
                                             @Param("tenantId") Long tenantId);

    ProcessEntity selectLatestNonEmptyByMacAndTenant(@Param("macAddress") String macAddress,
                                                     @Param("tenantId") Long tenantId);
}
