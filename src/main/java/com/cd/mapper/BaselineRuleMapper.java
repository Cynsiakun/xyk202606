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
}
