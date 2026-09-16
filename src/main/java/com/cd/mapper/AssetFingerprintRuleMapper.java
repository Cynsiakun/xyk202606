package com.cd.mapper;

import com.cd.entity.AssetFingerprintRuleEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AssetFingerprintRuleMapper {

    List<AssetFingerprintRuleEntity> selectEnabledRules();

    List<AssetFingerprintRuleEntity> selectManagePage(@Param("keyword") String keyword,
                                                      @Param("category") String category,
                                                      @Param("port") Integer port,
                                                      @Param("enabled") Integer enabled,
                                                      @Param("offset") int offset,
                                                      @Param("size") int size);

    long countManagePage(@Param("keyword") String keyword,
                         @Param("category") String category,
                         @Param("port") Integer port,
                         @Param("enabled") Integer enabled);

    AssetFingerprintRuleEntity selectById(@Param("id") Long id);

    AssetFingerprintRuleEntity selectByRuleCode(@Param("ruleCode") String ruleCode);

    int insertManage(AssetFingerprintRuleEntity entity);

    int updateManage(AssetFingerprintRuleEntity entity);

    int deleteById(@Param("id") Long id);
}
