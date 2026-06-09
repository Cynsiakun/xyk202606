package com.cd.service.impl;

import com.cd.common.exception.ResourceNotFoundException;
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
        if (hostId == null) {
            return List.of();
        }

        HostEntity host = hostMapper.selectById(hostId);
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
                    HostVulnResultEntity result = buildResult(hostId, rule, asset);
                    results.add(ruleEnrichmentService.enrich(result, rule, asset));
                } catch (Exception e) {
                    log.warn("漏洞规则匹配失败: hostId={}, ruleId={}, assetType={}, assetName={}",
                            hostId, rule.getId(), asset.getType(), asset.getName(), e);
                }
            }
        }

        hostVulnResultMapper.markInactiveByHostId(hostId);
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
                .name(host.getOsName())
                .version(firstText(host.getOsVersion(), host.getOsRelease()))
                .source("hosts")
                .build());

        if (!StringUtils.hasText(host.getMacAddress())) {
            return assets;
        }

        AppEntity appRecord = appMapper.selectLatestByMac(host.getMacAddress());
        ServiceEntity serviceRecord = serviceMapper.selectLatestByMac(host.getMacAddress());
        ProcessEntity processRecord = processMapper.selectLatestByMac(host.getMacAddress());

        assets.addAll(parseAssetJson(TYPE_APP, appRecord == null ? null : appRecord.getAssetJson()));
        assets.addAll(parseAssetJson(TYPE_SERVICE, serviceRecord == null ? null : serviceRecord.getAssetJson()));
        assets.addAll(parseAssetJson(TYPE_PROCESS, processRecord == null ? null : processRecord.getAssetJson()));

        for (AssetInfoDTO asset : assets) {
            asset.setHostId(host.getId());
        }
        return assets;
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
                String name = switch (type) {
                    case TYPE_APP -> firstText(item, "name", "software_name", "softwareName", "Name", "DisplayName", "displayName");
                    case TYPE_SERVICE -> firstText(item, "Name", "name", "DisplayName", "displayName", "service_name", "serviceName");
                    case TYPE_PROCESS -> firstText(item, "name", "Name", "process_name", "processName", "exe", "path");
                    default -> firstText(item, "name", "Name");
                };
                String cmd = firstText(item, "cmd", "Cmd", "command", "Command", "CommandLine", "commandLine");
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
                        .command(cmd)
                        .source(type + ".asset_json")
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
        List<VulnRuleEntity> candidates = vulnRuleCacheService.getRulesByType(asset.getType());
        List<VulnRuleEntity> matches = new ArrayList<>();
        for (VulnRuleEntity rule : candidates) {
            String ruleName = normalizeName(rule.getProductName());
            if (!StringUtils.hasText(ruleName)) {
                continue;
            }
            if (assetName.equals(ruleName) || assetName.contains(ruleName) || ruleName.contains(assetName)) {
                matches.add(rule);
            }
        }
        return matches;
    }

    private HostVulnResultEntity buildResult(Long hostId, VulnRuleEntity rule, AssetInfoDTO asset) {
        HostVulnResultEntity result = new HostVulnResultEntity();
        result.setHostId(hostId);
        result.setRuleId(rule.getId());
        result.setSeverity(rule.getSeverity());
        result.setVulnName(rule.getTitle());
        result.setProductName(asset.getName());
        result.setProductVersion(asset.getVersion());
        result.setSuggestion(rule.getSuggestion());
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

    private String extractVersion(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Matcher matcher = VERSION_PATTERN.matcher(value);
        return matcher.find() ? matcher.group() : null;
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
}
