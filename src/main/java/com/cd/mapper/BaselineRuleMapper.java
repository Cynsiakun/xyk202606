package com.cd.mapper;

import com.cd.entity.BaselineRuleEntity;
import com.cd.entity.BaselineRuleItemEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BaselineRuleMapper {

    List<BaselineRuleEntity> selectPublishedByIds(@Param("ids") List<Long> ids);

    List<BaselineRuleItemEntity> selectItemsByRuleIds(@Param("ruleIds") List<Long> ruleIds);

    /** 按规则项主键批量查询规则项，供规则引擎按 itemId 反查比对参数。 */
    List<BaselineRuleItemEntity> selectItemsByItemIds(@Param("itemIds") List<Long> itemIds);

    /** 按规则主键批量查询规则（不限状态），供规则引擎读取评分等元数据。 */
    List<BaselineRuleEntity> selectByIds(@Param("ids") List<Long> ids);

    BaselineRuleEntity selectById(@Param("id") Long id);

    BaselineRuleEntity selectByRuleCode(@Param("ruleCode") String ruleCode);

    List<BaselineRuleEntity> selectManagePage(@Param("keyword") String keyword,
                                              @Param("category") String category,
                                              @Param("severity") String severity,
                                              @Param("status") String status,
                                              @Param("enabled") Integer enabled,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    long countManagePage(@Param("keyword") String keyword,
                         @Param("category") String category,
                         @Param("severity") String severity,
                         @Param("status") String status,
                         @Param("enabled") Integer enabled);

    int insertManage(BaselineRuleEntity entity);

    int updateManage(BaselineRuleEntity entity);

    int archiveById(@Param("id") Long id);

    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);
}
