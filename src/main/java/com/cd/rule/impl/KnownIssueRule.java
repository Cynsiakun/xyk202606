package com.cd.rule.impl;

import com.cd.dto.RiskContext;
import com.cd.mapper.PatchRiskRuleMapper;
import com.cd.rule.PatchRiskRule;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Order(2)
@Component
@RequiredArgsConstructor
public class KnownIssueRule implements PatchRiskRule {

    private final PatchRiskRuleMapper patchRiskRuleMapper;

    @Override
    public List<RiskContext> execute(Long hostId) {
        return patchRiskRuleMapper.selectKnownIssueRisks(hostId);
    }
}
