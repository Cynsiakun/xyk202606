package com.cd.service.impl;

import com.cd.dto.RiskContext;
import com.cd.entity.HostPatchRiskEntity;
import com.cd.mapper.HostPatchRiskMapper;
import com.cd.rule.PatchRiskRule;
import com.cd.service.RiskLevelEvaluator;
import com.cd.service.RuleEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RuleEngineServiceImpl implements RuleEngineService {

    private final List<PatchRiskRule> rules;
    private final RiskLevelEvaluator riskLevelEvaluator;
    private final HostPatchRiskMapper hostPatchRiskMapper;

    @Override
    public List<HostPatchRiskEntity> execute(Long hostId) {
        if (hostId == null) {
            return List.of();
        }

        List<RiskContext> contexts = new ArrayList<>();
        for (PatchRiskRule rule : rules) {
            contexts.addAll(rule.execute(hostId));
        }
        List<HostPatchRiskEntity> risks = riskLevelEvaluator.evaluate(contexts);
        for (HostPatchRiskEntity risk : risks) {
            upsertRisk(risk);
        }
        return risks;
    }

    private void upsertRisk(HostPatchRiskEntity risk) {
        HostPatchRiskEntity existing = hostPatchRiskMapper.selectByRiskId(risk.getRiskId());
        if (existing == null) {
            hostPatchRiskMapper.insert(risk);
            return;
        }
        risk.setId(existing.getId());
        hostPatchRiskMapper.updateById(risk);
    }
}
