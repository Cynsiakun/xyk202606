package com.cd.rule;

import com.cd.dto.RiskContext;

import java.util.List;

public interface PatchRiskRule {

    List<RiskContext> execute(Long hostId);
}
