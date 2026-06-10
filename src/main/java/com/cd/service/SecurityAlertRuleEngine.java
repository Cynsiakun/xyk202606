package com.cd.service;

import com.cd.entity.SecurityAlertEntity;
import com.cd.security.SecurityEventContext;

import java.util.List;

public interface SecurityAlertRuleEngine {

    List<SecurityAlertEntity> evaluate(List<SecurityEventContext> events);
}
