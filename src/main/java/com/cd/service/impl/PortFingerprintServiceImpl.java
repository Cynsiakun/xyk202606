package com.cd.service.impl;

import com.cd.entity.AssetFingerprintRuleEntity;
import com.cd.entity.HostAssetInventoryEntity;
import com.cd.entity.HostEntity;
import com.cd.entity.PortScanResultEntity;
import com.cd.mapper.AssetFingerprintRuleMapper;
import com.cd.mapper.HostAssetInventoryMapper;
import com.cd.mapper.HostMapper;
import com.cd.mapper.PortScanResultMapper;
import com.cd.service.PortFingerprintService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortFingerprintServiceImpl implements PortFingerprintService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SOURCE_PLATFORM = "PLATFORM";

    private final PortScanResultMapper portScanResultMapper;
    private final AssetFingerprintRuleMapper assetFingerprintRuleMapper;
    private final HostAssetInventoryMapper hostAssetInventoryMapper;
    private final HostMapper hostMapper;

    @Override
    public void processExistingResults() {
        List<PortScanResultEntity> latestResults = portScanResultMapper.selectLatestPerMac();
        if (latestResults == null || latestResults.isEmpty()) {
            log.info("端口指纹历史回填跳过: 暂无端口扫描结果");
            return;
        }
        int success = 0;
        for (PortScanResultEntity result : latestResults) {
            try {
                processPortScanResult(result.getId());
                success++;
            } catch (Exception e) {
                log.warn("端口指纹历史回填失败: resultId={}", result.getId(), e);
            }
        }
        log.info("端口指纹历史回填完成: total={}, success={}", latestResults.size(), success);
    }

    @Override
    public void processPortScanResult(Long resultId) {
        if (resultId == null) {
            return;
        }
        PortScanResultEntity result = portScanResultMapper.selectById(resultId);
        if (result == null) {
            log.warn("端口指纹识别跳过: 端口结果不存在 resultId={}", resultId);
            return;
        }
        if (!StringUtils.hasText(result.getPortJson())) {
            log.warn("端口指纹识别跳过: 端口结果 JSON 为空 resultId={}", resultId);
            return;
        }

        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(result.getPortJson());
        } catch (Exception e) {
            log.warn("端口指纹识别跳过: 端口结果 JSON 非法 resultId={}", resultId, e);
            return;
        }

        JsonNode openPorts = root.path("openPorts");
        if (!openPorts.isArray()) {
            log.warn("端口指纹识别跳过: openPorts 不是数组 resultId={}", resultId);
            return;
        }

        String effectiveTaskId = resolveTaskId(result, root);
        List<AssetFingerprintRuleEntity> rules = sortRules(assetFingerprintRuleMapper.selectEnabledRules());
        if (rules.isEmpty()) {
            log.warn("资产指纹规则库为空，按 UNMATCHED 兜底: resultId={}", resultId);
        }

        HostEntity host = resolveHost(result);
        if (host == null || host.getId() == null) {
            log.warn("端口指纹识别跳过: 无法解析主机 resultId={}, mac={}", resultId, result.getMacAddress());
            return;
        }

        hostAssetInventoryMapper.deleteByHostIdAndTaskId(host.getId(), effectiveTaskId);
        List<HostAssetInventoryEntity> items = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (JsonNode portNode : openPorts) {
            Integer port = integerValue(portNode, "port");
            if (port == null) {
                continue;
            }
            String protocol = emptyToDefault(text(portNode, "protocol"), "tcp");
            String service = normalize(text(portNode, "service"));
            String banner = text(portNode, "banner");

            MatchResult matched = match(rules, port, service, banner);
            HostAssetInventoryEntity entity = new HostAssetInventoryEntity();
            entity.setTenantId(result.getTenantId() == null ? 0L : result.getTenantId());
            entity.setHostId(host.getId());
            entity.setMacAddress(host.getMacAddress());
            entity.setTaskId(effectiveTaskId);
            entity.setSource(defaultSource(result.getSource()));
            entity.setCategory(matched.category());
            entity.setSubCategory(matched.subCategory());
            entity.setVendor(matched.vendor());
            entity.setProductName(matched.productName());
            entity.setProductType(matched.productType());
            entity.setProductVersion(null);
            entity.setProtocol(protocol);
            entity.setPort(port);
            entity.setConfidence(matched.confidence());
            entity.setRuleId(matched.ruleId());
            entity.setBannerRaw(limitBanner(banner));
            entity.setDeleted(0);
            entity.setFirstSeen(now);
            entity.setLastSeen(now);
            items.add(entity);
        }

        if (!items.isEmpty()) {
            hostAssetInventoryMapper.insertBatch(items);
        }
        log.info("端口指纹识别完成: resultId={}, hostId={}, taskId={}, inventoryCount={}",
                resultId, host.getId(), effectiveTaskId, items.size());
    }

    @Override
    public int rematchByTask(Long inventoryId, Long tenantId) {
        Long safeTenantId = tenantId == null ? 0L : tenantId;
        HostAssetInventoryEntity task = hostAssetInventoryMapper.selectTaskByIdAndTenant(inventoryId, safeTenantId);
        if (task == null || !StringUtils.hasText(task.getTaskId())) {
            throw new IllegalArgumentException("未找到端口资产任务记录: id=" + inventoryId);
        }
        PortScanResultEntity result = portScanResultMapper.selectLatestByTaskIdAndTenant(task.getTaskId(), safeTenantId);
        if (result == null) {
            throw new IllegalArgumentException("未找到对应的端口扫描原始结果: taskId=" + task.getTaskId());
        }
        processPortScanResult(result.getId());
        List<HostAssetInventoryEntity> entities = hostAssetInventoryMapper.selectByHostIdAndTaskIdAndTenant(
                task.getHostId(), task.getTaskId(), safeTenantId);
        int count = entities == null ? 0 : entities.size();
        log.info("端口资产重新匹配完成: inventoryId={}, taskId={}, tenantId={}, count={}",
                inventoryId, task.getTaskId(), safeTenantId, count);
        return count;
    }

    @Override
    public int rematchLatestByMac(String macAddress, Long tenantId) {
        if (!StringUtils.hasText(macAddress)) {
            throw new IllegalArgumentException("macAddress不能为空");
        }
        Long safeTenantId = tenantId == null ? 0L : tenantId;
        PortScanResultEntity result = portScanResultMapper.selectLatestByMacAndTenant(macAddress, safeTenantId);
        if (result == null) {
            throw new IllegalArgumentException("未找到该主机的端口扫描结果: mac=" + macAddress);
        }
        processPortScanResult(result.getId());
        HostEntity host = resolveHost(result);
        if (host == null || host.getId() == null) {
            return 0;
        }
        String taskId = resolveTaskId(result, safeReadTree(result.getPortJson()));
        List<HostAssetInventoryEntity> entities = hostAssetInventoryMapper.selectByHostIdAndTaskIdAndTenant(
                host.getId(), taskId, safeTenantId);
        int count = entities == null ? 0 : entities.size();
        log.info("主机端口资产重新匹配完成: mac={}, taskId={}, tenantId={}, count={}",
                macAddress, taskId, safeTenantId, count);
        return count;
    }

    private MatchResult match(List<AssetFingerprintRuleEntity> rules, Integer port, String service, String banner) {
        MatchResult matched = matchByBannerRegex(rules, port, banner);
        if (matched != null) {
            return matched;
        }
        matched = matchByPortService(rules, port, service);
        if (matched != null) {
            return matched;
        }
        matched = matchByPortFallback(rules, port);
        if (matched != null) {
            return matched;
        }
        return new MatchResult("unknown", null, null, "UNMATCHED", "unknown", 0, null);
    }

    private MatchResult matchByBannerRegex(List<AssetFingerprintRuleEntity> rules, Integer port, String banner) {
        if (!StringUtils.hasText(banner)) {
            return null;
        }
        for (AssetFingerprintRuleEntity rule : rules) {
            if (rule.getPort() != null && !rule.getPort().equals(port)) {
                continue;
            }
            if (!StringUtils.hasText(rule.getBannerRegex())) {
                continue;
            }
            try {
                if (Pattern.compile(rule.getBannerRegex(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                        .matcher(banner)
                        .find()) {
                    return toMatchResult(rule);
                }
            } catch (Exception e) {
                log.warn("端口指纹 banner_regex 匹配异常: ruleId={}, regex={}", rule.getId(), rule.getBannerRegex(), e);
            }
        }
        return null;
    }

    private MatchResult matchByPortService(List<AssetFingerprintRuleEntity> rules, Integer port, String service) {
        if (!StringUtils.hasText(service)) {
            return null;
        }
        for (AssetFingerprintRuleEntity rule : rules) {
            if (rule.getPort() != null && !rule.getPort().equals(port)) {
                continue;
            }
            if (matchesService(service, rule)) {
                return toMatchResult(rule);
            }
        }
        return null;
    }

    private MatchResult matchByPortFallback(List<AssetFingerprintRuleEntity> rules, Integer port) {
        for (AssetFingerprintRuleEntity rule : rules) {
            if (!isFallbackRule(rule)) {
                continue;
            }
            if (rule.getPort() != null && rule.getPort().equals(port)) {
                return toMatchResult(rule);
            }
        }
        return null;
    }

    private MatchResult toMatchResult(AssetFingerprintRuleEntity rule) {
        return new MatchResult(
                emptyToDefault(rule.getCategory(), "unknown"),
                emptyToDefault(rule.getSubCategory(), null),
                emptyToDefault(rule.getVendor(), null),
                emptyToDefault(rule.getProduct(), "UNMATCHED"),
                emptyToDefault(rule.getCategory(), "unknown"),
                rule.getConfidence() == null ? 0 : rule.getConfidence(),
                rule.getId()
        );
    }

    private List<AssetFingerprintRuleEntity> sortRules(List<AssetFingerprintRuleEntity> rules) {
        if (rules == null || rules.isEmpty()) {
            return List.of();
        }
        return rules.stream()
                .sorted(Comparator
                        .comparing((AssetFingerprintRuleEntity it) -> it.getPriority() == null ? Integer.MAX_VALUE : it.getPriority())
                        .thenComparing(it -> it.getId() == null ? Long.MAX_VALUE : it.getId()))
                .toList();
    }

    private HostEntity resolveHost(PortScanResultEntity result) {
        if (result == null || !StringUtils.hasText(result.getMacAddress())) {
            return null;
        }
        String normalizedMac = result.getMacAddress().toLowerCase(Locale.ROOT).replaceAll("[^0-9a-f]", "");
        Long tenantId = result.getTenantId() == null ? 0L : result.getTenantId();
        HostEntity host = hostMapper.selectByNormalizedMacAndTenant(normalizedMac, tenantId);
        if (host == null) {
            host = hostMapper.selectByNormalizedMac(normalizedMac);
        }
        return host;
    }

    private String resolveTaskId(PortScanResultEntity result, JsonNode root) {
        String taskId = text(root, "taskId");
        if (!StringUtils.hasText(taskId)) {
            taskId = result.getTaskId();
        }
        if (!StringUtils.hasText(taskId)) {
            taskId = "port-scan-result-" + result.getId();
        }
        return taskId;
    }

    private Integer integerValue(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (value.isMissingNode() || value.isNull() || !value.canConvertToInt()) {
            return null;
        }
        return value.asInt();
    }

    private String text(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (value.isMissingNode() || value.isNull() || !value.isValueNode()) {
            return null;
        }
        String text = value.asText();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String emptyToDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private boolean matchesService(String service, AssetFingerprintRuleEntity rule) {
        String serviceText = normalize(service);
        if (!StringUtils.hasText(serviceText)) {
            return false;
        }
        return matchesServiceCandidate(serviceText, normalize(rule.getSubCategory()))
                || matchesServiceCandidate(serviceText, normalize(rule.getProduct()))
                || matchesServiceCandidate(serviceText, normalize(rule.getCategory()));
    }

    private boolean matchesServiceCandidate(String service, String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return false;
        }
        return service.equals(candidate)
                || service.contains(candidate)
                || candidate.contains(service)
                || service.equals(candidate.replace(' ', '-'))
                || service.equals(candidate.replace(' ', '_'));
    }

    private boolean isFallbackRule(AssetFingerprintRuleEntity rule) {
        String ruleCode = normalize(rule.getRuleCode());
        String description = normalize(rule.getDescription());
        return (ruleCode != null && ruleCode.startsWith("pfb-"))
                || (description != null && description.contains("[port_fallback]"));
    }

    private String limitBanner(String banner) {
        if (banner == null) {
            return null;
        }
        return banner.length() <= 12000 ? banner : banner.substring(0, 12000);
    }

    private JsonNode safeReadTree(String json) {
        if (!StringUtils.hasText(json)) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            log.warn("绔彛閲嶆柊鍖归厤鏃惰鍙栧師濮婣SON澶辫触", e);
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private String defaultSource(String source) {
        return StringUtils.hasText(source) ? source : SOURCE_PLATFORM;
    }

    private record MatchResult(String category,
                               String subCategory,
                               String vendor,
                               String productName,
                               String productType,
                               Integer confidence,
                               Long ruleId) {
    }

    @Configuration
    @RequiredArgsConstructor
    static class PortFingerprintBootstrap {

        private final PortFingerprintService portFingerprintService;

        @Bean
        ApplicationRunner portFingerprintBackfillRunner() {
            return args -> portFingerprintService.processExistingResults();
        }
    }
}
