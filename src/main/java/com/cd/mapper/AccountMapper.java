package com.cd.mapper;

import com.cd.entity.AccountEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 资产探测 — 账号数据持久化。
 */
public interface AccountMapper {

    int insert(AccountEntity entity);

    AccountEntity selectById(@Param("id") Long id);

    List<AccountEntity> selectPage(@Param("offset") int offset,
                                   @Param("size") int size,
                                   @Param("keyword") String keyword,
                                   @Param("macAddress") String macAddress);

    long countFiltered(@Param("keyword") String keyword,
                       @Param("macAddress") String macAddress);

    int softDeleteById(@Param("id") Long id);

    int updateAssetJsonById(@Param("id") Long id,
                            @Param("assetJson") String assetJson,
                            @Param("assetCount") Integer assetCount);

    /** 每个 MAC 的最新一条记录（用于总览聚合）。 */
    List<AccountEntity> selectLatestPerMac();
}
