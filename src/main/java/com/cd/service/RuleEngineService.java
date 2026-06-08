package com.cd.service;

import com.cd.entity.HostPatchRiskEntity;

import java.util.List;

public interface RuleEngineService {

    List<HostPatchRiskEntity> execute(Long hostId);
}
