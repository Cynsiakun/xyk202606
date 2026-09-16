package com.cd.mapper;

import com.cd.dto.AccountRiskSnapshotDTO;
import com.cd.entity.AccountEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 资产探测 — 账号数据持久化。
 */
public interface AccountMapper {

    int insert(AccountEntity entity);

    AccountEntity selectById(@Param("id") Long id);

    AccountEntity selectByIdAndTenant(@Param("id") Long id, @Param("tenantId") Long tenantId);

    List<AccountEntity> selectPage(@Param("offset") int offset,
                                   @Param("size") int size,
                                   @Param("keyword") String keyword,
                                   @Param("macAddress") String macAddress);

    List<AccountEntity> selectPageByTenant(@Param("offset") int offset,
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

    /** 每个 MAC 的最新一条记录（用于总览聚合）。 */
    List<AccountEntity> selectLatestPerMac();

    List<AccountEntity> selectLatestPerMacByTenant(@Param("tenantId") Long tenantId);

    List<AccountRiskSnapshotDTO> selectLatestRiskSnapshotByHostIds(@Param("hostIds") List<Long> hostIds);
}
