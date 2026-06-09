package com.cd.service.impl;

import com.cd.entity.VulnRuleEntity;
import com.cd.mapper.VulnRuleMapper;
import com.cd.service.VulnRuleCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VulnRuleCacheServiceImpl implements VulnRuleCacheService {

    private final VulnRuleMapper vulnRuleMapper;
    private final AtomicReference<Map<String, List<VulnRuleEntity>>> ruleCache = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<String, List<VulnRuleEntity>>> rulesByType = new AtomicReference<>(Map.of());

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        refresh();
    }

    @Override
    public Map<String, List<VulnRuleEntity>> getRuleCache() {
        return ruleCache.get();
    }

    @Override
    public List<VulnRuleEntity> getRulesByType(String productType) {
        String normalizedType = normalize(productType);
        return rulesByType.get().getOrDefault(normalizedType, List.of());
    }

    @Override
    public void refresh() {
        List<VulnRuleEntity> rules = vulnRuleMapper.selectEnabledRules();
        Map<String, List<VulnRuleEntity>> nextCache = new LinkedHashMap<>();
        Map<String, List<VulnRuleEntity>> nextByType = new LinkedHashMap<>();

        for (VulnRuleEntity rule : rules) {
            if (!StringUtils.hasText(rule.getProductType()) || !StringUtils.hasText(rule.getProductName())) {
                continue;
            }
            String type = normalize(rule.getProductType());
            String name = normalize(rule.getProductName());
            nextCache.computeIfAbsent(type + ":" + name, key -> new ArrayList<>()).add(rule);
            nextByType.computeIfAbsent(type, key -> new ArrayList<>()).add(rule);
        }

        ruleCache.set(immutableListMap(nextCache));
        rulesByType.set(immutableListMap(nextByType));
        log.info("漏洞规则缓存加载完成: enabledRules={}, cacheKeys={}", rules.size(), nextCache.size());
    }

    private Map<String, List<VulnRuleEntity>> immutableListMap(Map<String, List<VulnRuleEntity>> source) {
        return Collections.unmodifiableMap(source.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue()),
                        (left, right) -> left,
                        LinkedHashMap::new)));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
