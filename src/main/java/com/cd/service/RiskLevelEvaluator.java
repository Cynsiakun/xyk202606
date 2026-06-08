package com.cd.service;

import com.cd.dto.RiskContext;
import com.cd.entity.HostPatchRiskEntity;

import java.util.List;

public interface RiskLevelEvaluator {

    List<HostPatchRiskEntity> evaluate(List<RiskContext> contexts);
}
