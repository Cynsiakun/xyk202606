package com.cd.mapper;

import com.cd.entity.VulnRuleEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface VulnRuleMapper {

    int insert(VulnRuleEntity entity);

    int updateById(VulnRuleEntity entity);

    int deleteById(@Param("id") Long id);

    int deleteBatch(@Param("ids") List<Long> ids);

    VulnRuleEntity selectById(@Param("id") Long id);

    VulnRuleEntity selectByRuleCode(@Param("ruleCode") String ruleCode);

    List<VulnRuleEntity> selectPage(@Param("offset") int offset,
                                    @Param("size") int size,
                                    @Param("ruleCode") String ruleCode,
                                    @Param("cveId") String cveId,
                                    @Param("productName") String productName,
                                    @Param("severity") String severity,
                                    @Param("enabled") Integer enabled);

    long countAll(@Param("ruleCode") String ruleCode,
                  @Param("cveId") String cveId,
                  @Param("productName") String productName,
                  @Param("severity") String severity,
                  @Param("enabled") Integer enabled);

    List<VulnRuleEntity> selectEnabledRules();
}
