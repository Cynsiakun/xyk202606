package com.cd.mapper;

import com.cd.entity.BaselineRuleEntity;
import com.cd.entity.BaselineRuleItemEntity;
import com.cd.entity.BaselineProtectionLevelEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BaselineRuleMapper {

    List<BaselineRuleEntity> selectPublishedByIds(@Param("ids") List<Long> ids);

    List<BaselineRuleEntity> selectPublishedByIdsAndAssetTypes(@Param("ids") List<Long> ids,
                                                               @Param("assetTypeCodes") List<String> assetTypeCodes);

    List<BaselineRuleItemEntity> selectItemsByRuleIds(@Param("ruleIds") List<Long> ruleIds);

    List<BaselineRuleItemEntity> selectItemsByRuleIdsAndProtectionLevel(@Param("ruleIds") List<Long> ruleIds,
                                                                        @Param("protectionLevelId") Long protectionLevelId);

    /** 按规则项主键批量查询规则项，供规则引擎按 itemId 反查比对参数。 */
    List<BaselineRuleItemEntity> selectItemsByItemIds(@Param("itemIds") List<Long> itemIds);

    /** 按规则与检测项反查规则项，兼容旧客户端未回传 itemId 的结果。 */
    List<BaselineRuleItemEntity> selectItemsByRuleIdsAndCheckKeys(@Param("ruleIds") List<Long> ruleIds,
                                                                  @Param("checkKeys") List<String> checkKeys,
                                                                  @Param("protectionLevelId") Long protectionLevelId);

    /** 查询规则适用的首个资产类型，用于结果冗余维度落库。 */
    Long selectFirstAssetTypeIdByRuleId(@Param("ruleId") Long ruleId);

    /** 按规则主键批量查询规则（不限状态），供规则引擎读取评分等元数据。 */
    List<BaselineRuleEntity> selectByIds(@Param("ids") List<Long> ids);

    BaselineRuleEntity selectById(@Param("id") Long id);

    BaselineRuleEntity selectByRuleCode(@Param("ruleCode") String ruleCode);

    BaselineProtectionLevelEntity selectProtectionLevelById(@Param("id") Long id);

    BaselineProtectionLevelEntity selectProtectionLevelByCode(@Param("levelCode") String levelCode);

    List<BaselineRuleEntity> selectManagePage(@Param("keyword") String keyword,
                                              @Param("category") String category,
                                              @Param("severity") String severity,
                                              @Param("status") String status,
                                              @Param("enabled") Integer enabled,
                                              @Param("assetTypeCode") String assetTypeCode,
                                              @Param("protectionLevelCode") String protectionLevelCode,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    long countManagePage(@Param("keyword") String keyword,
                         @Param("category") String category,
                         @Param("severity") String severity,
                         @Param("status") String status,
                         @Param("enabled") Integer enabled,
                         @Param("assetTypeCode") String assetTypeCode,
                         @Param("protectionLevelCode") String protectionLevelCode);

    int insertManage(BaselineRuleEntity entity);

    int updateManage(BaselineRuleEntity entity);

    int archiveById(@Param("id") Long id);

    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);
}
