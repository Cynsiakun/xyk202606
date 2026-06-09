package com.cd.mapper;

import com.cd.entity.VulnRuleEntity;

import java.util.List;

public interface VulnRuleMapper {

    List<VulnRuleEntity> selectEnabledRules();
}
