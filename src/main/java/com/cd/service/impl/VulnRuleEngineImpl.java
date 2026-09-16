package com.cd.service.impl;

import com.cd.common.exception.ResourceNotFoundException;
import com.cd.common.security.TenantContextHolder;
import com.cd.dto.AssetInfoDTO;
import com.cd.entity.AppEntity;
import com.cd.entity.HostEntity;
import com.cd.entity.HostVulnResultEntity;
import com.cd.entity.ProcessEntity;
import com.cd.entity.ServiceEntity;
import com.cd.entity.VulnRuleEntity;
import com.cd.mapper.AppMapper;
import com.cd.mapper.HostMapper;
import com.cd.mapper.HostVulnResultMapper;
import com.cd.mapper.ProcessMapper;
import com.cd.mapper.ServiceMapper;
import com.cd.service.RuleEnrichmentService;
import com.cd.service.VulnRuleCacheService;
import com.cd.service.VulnRuleEngine;
import com.cd.util.VersionExpressionParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class VulnRuleEngineImpl implements VulnRuleEngine {

    private static final String TYPE_OS = "os";
    private static final String TYPE_APP = "app";
    private static final String TYPE_SERVICE = "service";
    private static final String TYPE_PROCESS = "process";
    private static final Pattern VERSION_PATTERN = Pattern.compile("(?<!\\d)\\d+(?:\\.\\d+)+(?:[-+._~a-zA-Z0-9]*)?");

    private final HostMapper hostMapper;
    private final AppMapper appMapper;
    private final ServiceMapper serviceMapper;
    private final ProcessMapper processMapper;
    private final HostVulnResultMapper hostVulnResultMapper;
    private final VulnRuleCacheService vulnRuleCacheService;
    private final RuleEnrichmentService ruleEnrichmentService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public List<HostVulnResultEntity> evaluateHost(Long hostId) {
        return evaluateHostInternal(hostId, currentTenantId());
    }

    @Override
    @Transactional
    public List<HostVulnResultEntity> evaluateHostForTenant(Long hostId, Long tenantId) {
        return evaluateHostInternal(hostId, tenantId == null ? 0L : tenantId);
    }

    private List<HostVulnResultEntity> evaluateHostInternal(Long hostId, Long tenantId) {
        if (hostId == null) {
            return List.of();
        }

        HostEntity host = hostMapper.selectByIdAndTenant(hostId, tenantId);
        if (host == null) {
            throw new ResourceNotFoundException("主机不存在: id=" + hostId);
        }

        List<AssetInfoDTO> assets = loadHostAssets(host);
        ensureRuleCacheLoaded();

        List<HostVulnResultEntity> results = new ArrayList<>();
        for (AssetInfoDTO asset : assets) {
            for (VulnRuleEntity rule : matchCandidateRules(asset)) {
                try {
                    if (!VersionExpressionParser.matches(rule.getMatchType(), rule.getAffectedVersionExpr(), asset)) {
                        continue;
                    }
                    HostVulnResultEntity result = buildResult(hostId, tenantId, rule, asset);
                    results.add(ruleEnrichmentService.enrich(result, rule, asset));
                } catch (Exception e) {
                    log.warn("漏洞规则匹配失败: hostId={}, ruleId={}, assetType={}, assetName={}",
                            hostId, rule.getId(), asset.getType(), asset.getName(), e);
                }
            }
        }

        hostVulnResultMapper.markInactiveByHostIdAndTenant(hostId, tenantId);
        for (HostVulnResultEntity result : results) {
            hostVulnResultMapper.insert(result);
        }
        return results;
    }

    private List<AssetInfoDTO> loadHostAssets(HostEntity host) {
        List<AssetInfoDTO> assets = new ArrayList<>();
        assets.add(AssetInfoDTO.builder()
                .hostId(host.getId())
                .type(TYPE_OS)
                .name(joinText(host.getOsName(), host.getOsRelease()))
                .version(firstText(host.getOsVersion(), host.getOsRelease()))
                .riskResult("os_name=" + valueOrDash(host.getOsName())
                        + ", os_version=" + valueOrDash(host.getOsVersion())
                        + ", os_detail=" + valueOrDash(host.getOsRelease()))
                .source("hosts")
                .build());

        if (!StringUtils.hasText(host.getMacAddress())) {
            return assets;
        }

        Long tenantId = host.getTenantId() == null ? currentTenantId() : host.getTenantId();
        assets.addAll(loadLatestAppAssets(host.getMacAddress(), tenantId));
        assets.addAll(loadLatestServiceAssets(host.getMacAddress(), tenantId));
        assets.addAll(loadLatestProcessAssets(host.getMacAddress(), tenantId));

        for (AssetInfoDTO asset : assets) {
            asset.setHostId(host.getId());
        }
        return assets;
    }

    private List<AssetInfoDTO> loadLatestAppAssets(String macAddress, Long tenantId) {
        AppEntity latestRecord = appMapper.selectLatestByMacAndTenant(macAddress, tenantId);
        List<AssetInfoDTO> latestAssets = parseAssetJson(TYPE_APP, latestRecord == null ? null : latestRecord.getAssetJson());
        if (!latestAssets.isEmpty()) {
            return latestAssets;
        }
        AppEntity latestNonEmptyRecord = appMapper.selectLatestNonEmptyByMacAndTenant(macAddress, tenantId);
        List<AssetInfoDTO> fallbackAssets = parseAssetJson(TYPE_APP, latestNonEmptyRecord == null ? null : latestNonEmptyRecord.getAssetJson());
        if (!fallbackAssets.isEmpty()) {
            log.info("Vuln match fallback to latest non-empty asset snapshot: type={}", TYPE_APP);
            return fallbackAssets;
        }
        return Collections.emptyList();
    }

    private List<AssetInfoDTO> loadLatestServiceAssets(String macAddress, Long tenantId) {
        ServiceEntity latestRecord = serviceMapper.selectLatestByMacAndTenant(macAddress, tenantId);
        List<AssetInfoDTO> latestAssets = parseAssetJson(TYPE_SERVICE, latestRecord == null ? null : latestRecord.getAssetJson());
        if (!latestAssets.isEmpty()) {
            return latestAssets;
        }
        ServiceEntity latestNonEmptyRecord = serviceMapper.selectLatestNonEmptyByMacAndTenant(macAddress, tenantId);
        List<AssetInfoDTO> fallbackAssets = parseAssetJson(TYPE_SERVICE, latestNonEmptyRecord == null ? null : latestNonEmptyRecord.getAssetJson());
        if (!fallbackAssets.isEmpty()) {
            log.info("Vuln match fallback to latest non-empty asset snapshot: type={}", TYPE_SERVICE);
            return fallbackAssets;
        }
        return Collections.emptyList();
    }

    private List<AssetInfoDTO> loadLatestProcessAssets(String macAddress, Long tenantId) {
        ProcessEntity latestRecord = processMapper.selectLatestByMacAndTenant(macAddress, tenantId);
        List<AssetInfoDTO> latestAssets = parseAssetJson(TYPE_PROCESS, latestRecord == null ? null : latestRecord.getAssetJson());
        if (!latestAssets.isEmpty()) {
            return latestAssets;
        }
        ProcessEntity latestNonEmptyRecord = processMapper.selectLatestNonEmptyByMacAndTenant(macAddress, tenantId);
        List<AssetInfoDTO> fallbackAssets = parseAssetJson(TYPE_PROCESS, latestNonEmptyRecord == null ? null : latestNonEmptyRecord.getAssetJson());
        if (!fallbackAssets.isEmpty()) {
            log.info("Vuln match fallback to latest non-empty asset snapshot: type={}", TYPE_PROCESS);
            return fallbackAssets;
        }
        return Collections.emptyList();
    }

    private List<AssetInfoDTO> parseAssetJson(String type, String assetJson) {
        if (!StringUtils.hasText(assetJson)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(assetJson);
            JsonNode items = root.isArray() ? root : root.path(arrayField(type));
            if (!items.isArray()) {
                return List.of();
            }

            List<AssetInfoDTO> assets = new ArrayList<>();
            for (JsonNode item : items) {
                String rawName = switch (type) {
                    case TYPE_APP -> firstText(item, "name", "software_name", "softwareName", "Name", "DisplayName", "displayName");
                    case TYPE_SERVICE -> firstText(item, "Name", "name", "DisplayName", "displayName", "service_name", "serviceName");
                    case TYPE_PROCESS -> firstText(item, "name", "Name", "process_name", "processName", "exe", "path");
                    default -> firstText(item, "name", "Name");
                };
                String cmd = firstText(item, "cmd", "Cmd", "command", "Command", "CommandLine", "commandLine");
                String path = firstText(item, "binpath", "binPath", "exe", "path", "Path");
                String displayName = firstText(item, "displayName", "DisplayName");
                String name = joinText(rawName, displayName);
                String version = firstText(item, "version", "Version", "product_version", "productVersion");
                if (!StringUtils.hasText(version)) {
                    version = extractVersion(firstText(name, cmd));
                }
                if (!StringUtils.hasText(name) && !StringUtils.hasText(cmd)) {
                    continue;
                }
                assets.add(AssetInfoDTO.builder()
                        .type(type)
                        .name(StringUtils.hasText(name) ? name : cmd)
                        .version(version)
                        .command(firstText(cmd, path))
                        .source(type + ".asset_json")
                        .riskLevel(firstText(item, "risk_level", "riskLevel"))
                        .riskScore(integerValue(item, "risk_score", "riskScore"))
                        .riskResult(firstText(item, "result", "risk_result", "riskResult"))
                        .suggestions(suggestionsText(item))
                        .build());
            }
            return assets;
        } catch (Exception e) {
            log.warn("资产JSON解析失败: type={}", type, e);
            return List.of();
        }
    }

    private String arrayField(String type) {
        return switch (type) {
            case TYPE_APP -> "apps";
            case TYPE_SERVICE -> "services";
            case TYPE_PROCESS -> "processes";
            default -> type;
        };
    }

    private void ensureRuleCacheLoaded() {
        if (vulnRuleCacheService.getRuleCache().isEmpty()) {
            vulnRuleCacheService.refresh();
        }
    }

    private List<VulnRuleEntity> matchCandidateRules(AssetInfoDTO asset) {
        if (!StringUtils.hasText(asset.getType()) || !StringUtils.hasText(asset.getName())) {
            return List.of();
        }
        String assetName = normalizeName(asset.getName());
        String assetText = normalizeName(joinText(asset.getName(), asset.getCommand()));
        List<VulnRuleEntity> candidates = vulnRuleCacheService.getRulesByType(asset.getType());
        List<VulnRuleEntity> matches = new ArrayList<>();
        for (VulnRuleEntity rule : candidates) {
            String ruleName = normalizeName(rule.getProductName());
            if (!StringUtils.hasText(ruleName)) {
                continue;
            }
            if (assetName.equals(ruleName)
                    || assetName.contains(ruleName)
                    || ruleName.contains(assetName)
                    || assetText.contains(ruleName)) {
                matches.add(rule);
            }
        }
        return matches;
    }

    private HostVulnResultEntity buildResult(Long hostId, Long tenantId, VulnRuleEntity rule, AssetInfoDTO asset) {
        HostVulnResultEntity result = new HostVulnResultEntity();
        result.setTenantId(tenantId);
        result.setHostId(hostId);
        result.setRuleId(rule.getId());
        result.setSeverity(rule.getSeverity());
        result.setVulnName(limit(rule.getTitle(), 255));
        result.setProductName(limit(asset.getName(), 255));
        result.setProductVersion(limit(asset.getVersion(), 128));
        result.setSuggestion(limit(rule.getSuggestion(), 1000));
        result.setStatus(1);
        result.setVerifyStatus("PENDING");
        result.setEvidenceJson(buildEvidenceJson(rule, asset));
        result.setCreatedAt(LocalDateTime.now());
        result.setUpdatedAt(LocalDateTime.now());
        return result;
    }

    private String buildEvidenceJson(VulnRuleEntity rule, AssetInfoDTO asset) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("type", asset.getType());
        evidence.put("name", asset.getName());
        evidence.put("version", asset.getVersion());
        if (StringUtils.hasText(asset.getCommand())) {
            evidence.put("cmd", asset.getCommand());
        }
        evidence.put("rule_expr", rule.getAffectedVersionExpr());
        evidence.put("match_type", rule.getMatchType());
        evidence.put("verify_type", rule.getVerifyType());
        evidence.put("verify_rule", rule.getVerifyRule());
        evidence.put("category", rule.getCategory());
        evidence.put("rule_code", rule.getRuleCode());
        evidence.put("reason", VersionExpressionParser.reason(rule.getMatchType()));
        evidence.put("rule_product_name", rule.getProductName());
        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private String joinText(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value) && !parts.contains(value.trim())) {
                parts.add(value.trim());
            }
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private String valueOrDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String extractVersion(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Matcher matcher = VERSION_PATTERN.matcher(value);
        return matcher.find() ? matcher.group() : null;
    }

    private Integer integerValue(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.canConvertToInt()) {
                return value.asInt();
            }
        }
        return null;
    }

    private String suggestionsText(JsonNode item) {
        JsonNode suggestions = item.get("suggestions");
        if (suggestions == null || suggestions.isNull()) {
            return null;
        }
        if (suggestions.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode suggestion : suggestions) {
                if (StringUtils.hasText(suggestion.asText())) {
                    values.add(suggestion.asText());
                }
            }
            return values.isEmpty() ? null : String.join("；", values);
        }
        return suggestions.asText();
    }

    private String firstText(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull() && StringUtils.hasText(value.asText())) {
                return value.asText().trim();
            }
        }
        return null;
    }

    private Long currentTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        return tenantId == null ? 0L : tenantId;
    }
}
