package com.cd.service.impl;

import com.cd.dto.AccountRiskSnapshotDTO;
import com.cd.entity.AccountChangeLogEntity;
import com.cd.entity.LoginSecurityLogEntity;
import com.cd.entity.SecurityAlertEntity;
import com.cd.entity.WindowsEventLogEntity;
import com.cd.mapper.AccountChangeLogMapper;
import com.cd.mapper.AccountMapper;
import com.cd.mapper.LoginSecurityLogMapper;
import com.cd.mapper.SecurityAlertMapper;
import com.cd.security.SecurityEventContext;
import com.cd.service.SecurityAlertRuleEngine;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SecurityAlertRuleEngineImpl implements SecurityAlertRuleEngine {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String LEVEL_CRITICAL = "Critical";
    private static final String LEVEL_HIGH = "High";
    private static final String LEVEL_MEDIUM = "Medium";

    private static final int BRUTE_FORCE_THRESHOLD = 5;
    private static final int PASSWORD_SPRAY_THRESHOLD = 5;
    private static final int FAIL_THEN_SUCCESS_THRESHOLD = 3;

    private static final Set<String> BUILTIN_RISKY_ACCOUNTS = Set.of(
            "administrator", "admin", "guest", "krbtgt", "root"
    );

    private static final Set<String> ASSET_RISK_TRIGGER_LEVELS = Set.of("HIGH");
    private static final Set<String> ASSET_RISK_STRONG_TAGS = Set.of(
            "高权限账号",
            "影子账号",
            "高权限组成员",
            "异常启用",
            "默认管理员账号",
            "privileged account",
            "shadow account"
    );

    private static final Set<String> PRIVILEGED_GROUPS = Set.of(
            "administrators",
            "domain admins",
            "enterprise admins",
            "schema admins",
            "account operators",
            "backup operators",
            "server operators",
            "remote desktop users"
    );

    private static final List<String> QUICK_LOGIN_ACTIONS = List.of(
            "create", "enable", "change_password", "reset_password", "add_to_group", "modify"
    );

    private final AccountMapper accountMapper;
    private final LoginSecurityLogMapper loginSecurityLogMapper;
    private final AccountChangeLogMapper accountChangeLogMapper;
    private final SecurityAlertMapper securityAlertMapper;

    @Override
    public List<SecurityAlertEntity> evaluate(List<SecurityEventContext> events) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }

        List<SecurityEventContext> sortedEvents = events.stream()
                .sorted(Comparator.comparing(ctx -> ctx.getEventLog().getEventTime()))
                .toList();

        Map<Long, HostWindowData> hostWindowData = loadHostWindowData(sortedEvents);
        List<SecurityAlertEntity> candidates = new ArrayList<>();

        for (SecurityEventContext event : sortedEvents) {
            HostWindowData hostData = hostWindowData.getOrDefault(event.getEventLog().getHostId(), HostWindowData.empty());
            candidates.addAll(evaluateAtomicRules(event, hostData));
        }
        for (SecurityEventContext event : sortedEvents) {
            HostWindowData hostData = hostWindowData.getOrDefault(event.getEventLog().getHostId(), HostWindowData.empty());
            candidates.addAll(evaluateWindowRules(event, hostData));
        }

        return deduplicateCandidates(candidates);
    }

    private Map<Long, HostWindowData> loadHostWindowData(List<SecurityEventContext> events) {
        Map<Long, List<SecurityEventContext>> byHost = events.stream()
                .collect(Collectors.groupingBy(ctx -> ctx.getEventLog().getHostId(), LinkedHashMap::new, Collectors.toList()));

        Map<Long, HostWindowData> result = new HashMap<>();
        for (Map.Entry<Long, List<SecurityEventContext>> entry : byHost.entrySet()) {
            Long hostId = entry.getKey();
            List<SecurityEventContext> hostEvents = entry.getValue();
            LocalDateTime minTime = hostEvents.stream()
                    .map(ctx -> ctx.getEventLog().getEventTime())
                    .filter(Objects::nonNull)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);
            LocalDateTime maxTime = hostEvents.stream()
                    .map(ctx -> ctx.getEventLog().getEventTime())
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            if (minTime == null || maxTime == null) {
                result.put(hostId, HostWindowData.empty());
                continue;
            }

            List<LoginSecurityLogEntity> recentFailed = loginSecurityLogMapper.selectFailedWithinWindow(
                    hostId, minTime.minusMinutes(10), maxTime);
            List<AccountChangeLogEntity> recentActions = accountChangeLogMapper.selectRecentActionsByHost(
                    hostId, QUICK_LOGIN_ACTIONS, minTime.minusMinutes(30), maxTime);
            result.put(hostId, new HostWindowData(recentFailed, recentActions, new HashMap<>()));
        }

        List<Long> hostIds = new ArrayList<>(result.keySet());
        if (!hostIds.isEmpty()) {
            for (AccountRiskSnapshotDTO snapshot : accountMapper.selectLatestRiskSnapshotByHostIds(hostIds)) {
                HostWindowData data = result.computeIfAbsent(snapshot.getHostId(), key -> HostWindowData.empty());
                data.assetRiskProfiles().putAll(parseAssetRiskProfiles(snapshot.getAssetJson()));
            }
        }
        return result;
    }

    private List<SecurityAlertEntity> evaluateAtomicRules(SecurityEventContext ctx, HostWindowData hostData) {
        WindowsEventLogEntity log = ctx.getEventLog();
        Map<String, String> data = ctx.getEventData();
        List<SecurityAlertEntity> alerts = new ArrayList<>();

        switch (log.getEventId()) {
            case 1102 -> alerts.add(buildAlert(
                    ctx, "LOG_CLEARED", LEVEL_CRITICAL, 95,
                    "审计日志被清除",
                    "检测到主机审计日志被清除，存在明显反取证风险。",
                    Map.of("eventId", 1102)));
            case 7045 -> alerts.add(buildAlert(
                    ctx, "SERVICE_INSTALLED", LEVEL_HIGH, 85,
                    "检测到新服务安装",
                    buildServiceInstallDescription(log, data),
                    serviceEvidence(data)));
            case 6008 -> alerts.add(buildAlert(
                    ctx, "ABNORMAL_SHUTDOWN", LEVEL_MEDIUM, 60,
                    "检测到非正常关机",
                    "主机发生非正常关机，建议结合上下文检查是否存在异常中断或规避审计行为。",
                    Map.of("eventId", 6008)));
            case 4720 -> alerts.add(buildAlert(
                    ctx, "ACCOUNT_CREATED", LEVEL_HIGH, 80,
                    "检测到新建账户",
                    "账户 " + displayName(ctx.getAccountLog() == null ? null : ctx.getAccountLog().getTargetUsername()) + " 被创建。",
                    accountEvidence(ctx.getAccountLog())));
            case 4722 -> alerts.add(buildAlert(
                    ctx, "ACCOUNT_ENABLED", LEVEL_HIGH, 80,
                    "检测到账户启用",
                    "账户 " + displayName(ctx.getAccountLog() == null ? null : ctx.getAccountLog().getTargetUsername()) + " 被启用。",
                    accountEvidence(ctx.getAccountLog())));
            case 4726 -> alerts.add(buildAlert(
                    ctx, "ACCOUNT_DELETED", LEVEL_HIGH, 80,
                    "检测到账户删除",
                    "账户 " + displayName(ctx.getAccountLog() == null ? null : ctx.getAccountLog().getTargetUsername()) + " 被删除。",
                    accountEvidence(ctx.getAccountLog())));
            case 4723, 4724, 4738 -> alerts.add(buildAlert(
                    ctx, "ACCOUNT_PASSWORD_CHANGED", LEVEL_HIGH, 82,
                    "检测到账户密码或属性变更",
                    buildAccountChangeDescription(ctx.getAccountLog(), log.getEventId()),
                    accountEvidence(ctx.getAccountLog())));
            case 4728 -> alerts.add(buildGroupMembershipAlert(ctx));
            case 4624 -> {
                if (isRiskyAccount(ctx.getLoginLog() == null ? null : ctx.getLoginLog().getUsername(), hostData)) {
                    alerts.add(buildAlert(
                            ctx, "RISKY_ACCOUNT_LOGIN", LEVEL_HIGH, 85,
                            "危险账号登录成功",
                            "危险账号 " + displayName(ctx.getLoginLog() == null ? null : ctx.getLoginLog().getUsername()) + " 登录成功。",
                            mergeEvidence(loginEvidence(ctx.getLoginLog()),
                                    assetRiskEvidence(ctx.getLoginLog() == null ? null : ctx.getLoginLog().getUsername(), hostData))));
                }
                if (isOffHours(log.getEventTime())) {
                    boolean riskyAccount = isRiskyAccount(ctx.getLoginLog() == null ? null : ctx.getLoginLog().getUsername(), hostData);
                    String level = riskyAccount ? LEVEL_HIGH : LEVEL_MEDIUM;
                    int score = riskyAccount ? 82 : 62;
                    alerts.add(buildAlert(
                            ctx, "OFF_HOURS_LOGIN", level, score,
                            "检测到非工作时段登录",
                            "账号 " + displayName(ctx.getLoginLog() == null ? null : ctx.getLoginLog().getUsername()) + " 在非工作时段登录成功。",
                            mergeEvidence(loginEvidence(ctx.getLoginLog()),
                                    assetRiskEvidence(ctx.getLoginLog() == null ? null : ctx.getLoginLog().getUsername(), hostData))));
                }
            }
            default -> {
            }
        }
        return alerts;
    }

    private List<SecurityAlertEntity> evaluateWindowRules(SecurityEventContext ctx, HostWindowData hostData) {
        LoginSecurityLogEntity login = ctx.getLoginLog();
        if (login == null) {
            return List.of();
        }

        List<SecurityAlertEntity> alerts = new ArrayList<>();
        if ("fail".equals(login.getLoginResult())) {
            alerts.addAll(evaluateFailedLoginWindows(ctx, hostData));
        } else if ("success".equals(login.getLoginResult())) {
            alerts.addAll(evaluateSuccessfulLoginWindows(ctx, hostData));
        }
        return alerts;
    }

    private List<SecurityAlertEntity> evaluateFailedLoginWindows(SecurityEventContext ctx, HostWindowData hostData) {
        LoginSecurityLogEntity login = ctx.getLoginLog();
        LocalDateTime eventTime = login.getEventTime();
        String normalizedUser = normalizePrincipal(login.getUsername());
        String sourceIp = normalizeSourceIp(login.getSourceIp());
        if (!StringUtils.hasText(normalizedUser)) {
            return List.of();
        }

        List<LoginSecurityLogEntity> prior5m = filterFailed(hostData.failedLogins(), eventTime.minusMinutes(5), eventTime);
        long currentCount = prior5m.stream()
                .filter(it -> principalEquals(it.getUsername(), normalizedUser) && sourceIpEquals(it.getSourceIp(), sourceIp))
                .count();
        long previousCount = prior5m.stream()
                .filter(it -> it.getEventTime().isBefore(eventTime))
                .filter(it -> principalEquals(it.getUsername(), normalizedUser) && sourceIpEquals(it.getSourceIp(), sourceIp))
                .count();

        List<SecurityAlertEntity> alerts = new ArrayList<>();
        if (currentCount >= BRUTE_FORCE_THRESHOLD && previousCount < BRUTE_FORCE_THRESHOLD) {
            alerts.add(buildAlert(
                    ctx, "BRUTE_FORCE_LOGIN", LEVEL_HIGH, 88,
                    "检测到暴力破解行为",
                    "账号 " + displayName(login.getUsername()) + " 在 5 分钟内连续登录失败 " + currentCount + " 次。",
                    Map.of(
                            "username", defaultString(login.getUsername()),
                            "sourceIp", defaultString(login.getSourceIp()),
                            "failCount", currentCount,
                            "windowMinutes", 5
                    )));
        }

        if (StringUtils.hasText(sourceIp)) {
            List<LoginSecurityLogEntity> prior10m = filterFailed(hostData.failedLogins(), eventTime.minusMinutes(10), eventTime);
            int currentDistinct = distinctUsersForIp(prior10m, sourceIp).size();
            int previousDistinct = distinctUsersForIp(prior10m.stream()
                    .filter(it -> it.getEventTime().isBefore(eventTime))
                    .toList(), sourceIp).size();
            if (currentDistinct >= PASSWORD_SPRAY_THRESHOLD && previousDistinct < PASSWORD_SPRAY_THRESHOLD) {
                alerts.add(buildAlert(
                        ctx, "PASSWORD_SPRAY", LEVEL_HIGH, 86,
                        "检测到密码喷洒行为",
                        "来源 IP " + sourceIp + " 在 10 分钟内对多个账号发生失败登录，涉及 " + currentDistinct + " 个账号。",
                        Map.of(
                                "sourceIp", sourceIp,
                                "distinctUserCount", currentDistinct,
                                "windowMinutes", 10
                        )));
            }
        }
        return alerts;
    }

    private List<SecurityAlertEntity> evaluateSuccessfulLoginWindows(SecurityEventContext ctx, HostWindowData hostData) {
        LoginSecurityLogEntity login = ctx.getLoginLog();
        LocalDateTime eventTime = login.getEventTime();
        String normalizedUser = normalizePrincipal(login.getUsername());
        String sourceIp = normalizeSourceIp(login.getSourceIp());
        List<SecurityAlertEntity> alerts = new ArrayList<>();

        long failCount = filterFailed(hostData.failedLogins(), eventTime.minusMinutes(10), eventTime).stream()
                .filter(it -> it.getEventTime().isBefore(eventTime))
                .filter(it -> principalEquals(it.getUsername(), normalizedUser) && sourceIpEquals(it.getSourceIp(), sourceIp))
                .count();
        if (failCount >= FAIL_THEN_SUCCESS_THRESHOLD) {
            alerts.add(buildAlert(
                    ctx, "FAIL_THEN_SUCCESS", LEVEL_HIGH, 87,
                    "检测到失败后成功登录",
                    "账号 " + displayName(login.getUsername()) + " 在多次失败后登录成功，疑似凭证被猜解。",
                    Map.of(
                            "username", defaultString(login.getUsername()),
                            "sourceIp", defaultString(login.getSourceIp()),
                            "precedingFailCount", failCount,
                            "windowMinutes", 10
                    )));
        }

        List<AccountChangeLogEntity> relatedActions = hostData.recentActions().stream()
                .filter(action -> action.getEventTime() != null
                        && !action.getEventTime().isAfter(eventTime)
                        && action.getEventTime().isAfter(eventTime.minusMinutes(30)))
                .filter(action -> principalEquals(action.getTargetUsername(), normalizedUser))
                .sorted(Comparator.comparing(AccountChangeLogEntity::getEventTime).reversed())
                .toList();

        AccountChangeLogEntity latestCreate = latestAction(relatedActions, "create");
        if (latestCreate != null) {
            alerts.add(buildAlert(
                    ctx, "NEW_ACCOUNT_QUICK_LOGIN", LEVEL_CRITICAL, 92,
                    "检测到新建账号快速登录",
                    "新建账号 " + displayName(login.getUsername()) + " 在创建后 30 分钟内登录成功。",
                    mergeEvidence(loginEvidence(login), accountEvidence(latestCreate))));
        }

        AccountChangeLogEntity latestEnable = latestAction(relatedActions, "enable");
        if (latestEnable != null) {
            alerts.add(buildAlert(
                    ctx, "ENABLED_ACCOUNT_QUICK_LOGIN", LEVEL_HIGH, 84,
                    "检测到启用账号后快速登录",
                    "账号 " + displayName(login.getUsername()) + " 在启用后 30 分钟内登录成功。",
                    mergeEvidence(loginEvidence(login), accountEvidence(latestEnable))));
        }

        AccountChangeLogEntity latestReset = latestAction(relatedActions, "reset_password", "change_password", "modify");
        if (latestReset != null) {
            alerts.add(buildAlert(
                    ctx, "PASSWORD_RESET_QUICK_LOGIN", LEVEL_HIGH, 84,
                    "检测到密码变更后快速登录",
                    "账号 " + displayName(login.getUsername()) + " 在密码或属性变更后 30 分钟内登录成功。",
                    mergeEvidence(loginEvidence(login), accountEvidence(latestReset))));
        }

        AccountChangeLogEntity latestPrivilege = latestAction(relatedActions, "add_to_group");
        if (latestPrivilege != null) {
            alerts.add(buildAlert(
                    ctx, "PRIVILEGE_CHANGE_QUICK_LOGIN", LEVEL_CRITICAL, 90,
                    "检测到权限变更后快速登录",
                    "账号 " + displayName(login.getUsername()) + " 在权限组变更后 30 分钟内登录成功。",
                    mergeEvidence(loginEvidence(login), accountEvidence(latestPrivilege))));
        }

        return alerts;
    }

    private void enrichAssetRiskProfiles(Map<Long, HostWindowData> hostData) {
        // Left intentionally blank: profiles are attached during loadHostWindowData().
    }

    private Map<String, AssetAccountRiskProfile> parseAssetRiskProfiles(String assetJson) {
        Map<String, AssetAccountRiskProfile> profiles = new HashMap<>();
        if (!StringUtils.hasText(assetJson)) {
            return profiles;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(assetJson);
            if (!root.isArray()) {
                return profiles;
            }
            for (JsonNode accountNode : root) {
                String accountName = text(accountNode, "name");
                String fullName = text(accountNode, "fullName");
                String level = normalizeUpper(text(accountNode, "risk_level"));
                int score = intValue(accountNode, "risk_score");
                Set<String> tags = readTags(accountNode.get("risk_tags"));
                String result = text(accountNode, "result");
                Boolean disabled = boolValue(accountNode, "disabled");

                AssetAccountRiskProfile profile = new AssetAccountRiskProfile(level, score, tags, result, disabled);
                if (StringUtils.hasText(accountName)) {
                    profiles.put(normalizePrincipal(accountName), profile);
                }
                if (StringUtils.hasText(fullName)) {
                    profiles.putIfAbsent(normalizePrincipal(fullName), profile);
                }
            }
        } catch (Exception ignored) {
            return profiles;
        }
        return profiles;
    }

    private List<SecurityAlertEntity> deduplicateCandidates(List<SecurityAlertEntity> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        Map<String, SecurityAlertEntity> uniqueBySourceAndRule = new LinkedHashMap<>();
        for (SecurityAlertEntity candidate : candidates) {
            uniqueBySourceAndRule.putIfAbsent(candidate.getSourceLogId() + "|" + candidate.getRuleCode(), candidate);
        }

        List<SecurityAlertEntity> unique = new ArrayList<>(uniqueBySourceAndRule.values());
        List<String> dedupKeys = unique.stream()
                .map(SecurityAlertEntity::getDedupKey)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        Set<String> existing = dedupKeys.isEmpty()
                ? Set.of()
                : new HashSet<>(securityAlertMapper.selectExistingDedupKeys(dedupKeys, LocalDateTime.now().minusHours(1)));

        List<SecurityAlertEntity> result = new ArrayList<>();
        Set<String> seenThisBatch = new HashSet<>();
        for (SecurityAlertEntity alert : unique) {
            String dedupKey = alert.getDedupKey();
            if (StringUtils.hasText(dedupKey) && (existing.contains(dedupKey) || !seenThisBatch.add(dedupKey))) {
                continue;
            }
            result.add(alert);
        }
        return result;
    }

    private SecurityAlertEntity buildGroupMembershipAlert(SecurityEventContext ctx) {
        Map<String, String> data = ctx.getEventData();
        String groupName = firstNonBlank(data.get("TargetUserName"), data.get("GroupName"));
        boolean privileged = isPrivilegedGroup(groupName);
        String level = privileged ? LEVEL_CRITICAL : LEVEL_HIGH;
        int score = privileged ? 93 : 83;
        String alertName = privileged ? "检测到高权限组成员变更" : "检测到安全组成员变更";
        String description = "账号 " + displayName(ctx.getAccountLog() == null ? null : ctx.getAccountLog().getTargetUsername())
                + " 被加入组 " + displayName(groupName) + "。";
        Map<String, Object> evidence = new LinkedHashMap<>(accountEvidence(ctx.getAccountLog()));
        evidence.put("groupName", defaultString(groupName));
        evidence.put("privilegedGroup", privileged);
        return buildAlert(ctx, "GROUP_MEMBERSHIP_CHANGED", level, score, alertName, description, evidence);
    }

    private SecurityAlertEntity buildAlert(SecurityEventContext ctx, String ruleCode, String level, int riskScore,
                                           String alertName, String description, Map<String, Object> evidence) {
        SecurityAlertEntity alert = new SecurityAlertEntity();
        alert.setTenantId(ctx.getEventLog().getTenantId() == null ? 0L : ctx.getEventLog().getTenantId());
        alert.setSourceLogId(ctx.getSourceLogId());
        alert.setHostId(ctx.getEventLog().getHostId());
        alert.setEventId(ctx.getEventLog().getEventId());
        alert.setRuleCode(ruleCode);
        alert.setDedupKey(buildDedupKey(ctx, ruleCode));
        alert.setAlertName(alertName);
        alert.setLevel(level);
        alert.setRiskScore(riskScore);
        alert.setDescription(truncate(description, 1000));
        alert.setEvidenceJson(toJson(evidence));
        alert.setStatus("new");
        alert.setEventTime(ctx.getEventLog().getEventTime());
        return alert;
    }

    private String buildDedupKey(SecurityEventContext ctx, String ruleCode) {
        WindowsEventLogEntity log = ctx.getEventLog();
        LoginSecurityLogEntity login = ctx.getLoginLog();
        AccountChangeLogEntity account = ctx.getAccountLog();
        String principal = login != null ? normalizePrincipal(login.getUsername()) : normalizePrincipal(account == null ? null : account.getTargetUsername());
        String sourceIp = login == null ? "-" : normalizeSourceIp(login.getSourceIp());
        return switch (ruleCode) {
            case "LOG_CLEARED", "ABNORMAL_SHUTDOWN" -> log.getHostId() + "|" + ruleCode;
            case "SERVICE_INSTALLED" -> log.getHostId() + "|" + ruleCode + "|" + normalizeText(firstNonBlank(
                    ctx.getEventData().get("ServiceName"), ctx.getEventData().get("ServiceFileName")));
            case "PASSWORD_SPRAY" -> log.getHostId() + "|" + ruleCode + "|" + sourceIp;
            case "BRUTE_FORCE_LOGIN", "FAIL_THEN_SUCCESS", "RISKY_ACCOUNT_LOGIN", "OFF_HOURS_LOGIN" ->
                    log.getHostId() + "|" + ruleCode + "|" + principal + "|" + sourceIp;
            default -> log.getHostId() + "|" + ruleCode + "|" + principal;
        };
    }

    private String buildServiceInstallDescription(WindowsEventLogEntity log, Map<String, String> data) {
        String serviceName = firstNonBlank(data.get("ServiceName"), data.get("ServiceFileName"));
        return "主机检测到新服务安装，服务名: " + displayName(serviceName)
                + "，触发事件 ID " + log.getEventId() + "。";
    }

    private String buildAccountChangeDescription(AccountChangeLogEntity accountLog, Integer eventId) {
        String subject = displayName(accountLog == null ? null : accountLog.getTargetUsername());
        return switch (eventId) {
            case 4723 -> "检测到账户 " + subject + " 的密码修改尝试。";
            case 4724 -> "检测到账户 " + subject + " 的密码重置尝试。";
            case 4738 -> "检测到账户 " + subject + " 的属性变更。";
            default -> "检测到账户变更。";
        };
    }

    private Map<String, Object> serviceEvidence(Map<String, String> data) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("serviceName", defaultString(firstNonBlank(data.get("ServiceName"), data.get("ServiceFileName"))));
        evidence.put("serviceAccount", defaultString(data.get("ServiceAccount")));
        evidence.put("serviceStartType", defaultString(data.get("StartType")));
        return evidence;
    }

    private Map<String, Object> accountEvidence(AccountChangeLogEntity accountLog) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        if (accountLog == null) {
            return evidence;
        }
        evidence.put("operatorUsername", defaultString(accountLog.getOperatorUsername()));
        evidence.put("targetUsername", defaultString(accountLog.getTargetUsername()));
        evidence.put("actionType", defaultString(accountLog.getActionType()));
        evidence.put("details", defaultString(accountLog.getDetails()));
        return evidence;
    }

    private Map<String, Object> loginEvidence(LoginSecurityLogEntity login) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        if (login == null) {
            return evidence;
        }
        evidence.put("username", defaultString(login.getUsername()));
        evidence.put("sourceIp", defaultString(login.getSourceIp()));
        evidence.put("loginType", login.getLoginType());
        evidence.put("processName", defaultString(login.getProcessName()));
        evidence.put("isElevated", login.getIsElevated());
        return evidence;
    }

    private Map<String, Object> assetRiskEvidence(String username, HostWindowData hostData) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        AssetAccountRiskProfile profile = hostData.assetRiskProfiles().get(normalizePrincipal(username));
        if (profile == null) {
            return evidence;
        }
        evidence.put("assetRiskLevel", defaultString(profile.level()));
        evidence.put("assetRiskScore", profile.score());
        evidence.put("assetRiskTags", profile.tags());
        evidence.put("assetRiskActionable", isActionableAssetRisk(profile));
        evidence.put("assetRiskResult", defaultString(profile.result()));
        evidence.put("assetAccountDisabled", profile.disabled());
        return evidence;
    }

    private Map<String, Object> mergeEvidence(Map<String, Object> left, Map<String, Object> right) {
        Map<String, Object> merged = new LinkedHashMap<>(left);
        merged.putAll(right);
        return merged;
    }

    private String toJson(Map<String, Object> evidence) {
        try {
            return OBJECT_MAPPER.writeValueAsString(evidence);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private List<LoginSecurityLogEntity> filterFailed(List<LoginSecurityLogEntity> logs, LocalDateTime since,
                                                      LocalDateTime until) {
        return logs.stream()
                .filter(it -> it.getEventTime() != null
                        && !it.getEventTime().isBefore(since)
                        && !it.getEventTime().isAfter(until))
                .toList();
    }

    private Set<String> distinctUsersForIp(Collection<LoginSecurityLogEntity> logs, String sourceIp) {
        return logs.stream()
                .filter(it -> sourceIpEquals(it.getSourceIp(), sourceIp))
                .map(LoginSecurityLogEntity::getUsername)
                .map(this::normalizePrincipal)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private AccountChangeLogEntity latestAction(List<AccountChangeLogEntity> actions, String... actionTypes) {
        Set<String> allowed = Set.of(actionTypes);
        return actions.stream()
                .filter(action -> allowed.contains(action.getActionType()))
                .findFirst()
                .orElse(null);
    }

    private boolean isPrivilegedGroup(String groupName) {
        String normalized = normalizeText(groupName);
        return PRIVILEGED_GROUPS.stream().anyMatch(normalized::contains);
    }

    private boolean isRiskyAccount(String username, HostWindowData hostData) {
        String normalized = normalizePrincipal(username);
        if (BUILTIN_RISKY_ACCOUNTS.contains(normalized)) {
            return true;
        }
        AssetAccountRiskProfile profile = hostData.assetRiskProfiles().get(normalized);
        return profile != null && isActionableAssetRisk(profile);
    }

    private boolean isActionableAssetRisk(AssetAccountRiskProfile profile) {
        if (profile == null) {
            return false;
        }
        if (ASSET_RISK_TRIGGER_LEVELS.contains(profile.level())) {
            return true;
        }
        if (!"MEDIUM".equals(profile.level())) {
            return false;
        }
        if (profile.score() < 60) {
            return false;
        }
        if (profile.tags().stream().anyMatch(this::containsStrongRiskKeyword)) {
            return true;
        }
        return containsStrongRiskKeyword(profile.result());
    }

    private boolean containsStrongRiskKeyword(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String normalized = normalizeText(text);
        return ASSET_RISK_STRONG_TAGS.stream().anyMatch(tag -> normalized.contains(normalizeText(tag)));
    }

    private boolean isOffHours(LocalDateTime eventTime) {
        if (eventTime == null) {
            return false;
        }
        int hour = eventTime.getHour();
        return hour >= 22 || hour < 6;
    }

    private boolean principalEquals(String rawUsername, String normalizedUsername) {
        return Objects.equals(normalizePrincipal(rawUsername), normalizedUsername);
    }

    private boolean sourceIpEquals(String rawSourceIp, String normalizedSourceIp) {
        return Objects.equals(normalizeSourceIp(rawSourceIp), normalizedSourceIp);
    }

    private String normalizePrincipal(String username) {
        if (!StringUtils.hasText(username)) {
            return "";
        }
        String normalized = username.trim();
        int slashIndex = normalized.lastIndexOf('\\');
        if (slashIndex >= 0 && slashIndex < normalized.length() - 1) {
            normalized = normalized.substring(slashIndex + 1);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeSourceIp(String sourceIp) {
        return normalizeText(sourceIp);
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeUpper(String text) {
        return text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
    }

    private String displayName(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private int intValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? 0 : value.asInt(0);
    }

    private Boolean boolValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asBoolean();
    }

    private Set<String> readTags(JsonNode tagsNode) {
        Set<String> tags = new LinkedHashSet<>();
        if (tagsNode == null || !tagsNode.isArray()) {
            return tags;
        }
        for (JsonNode tagNode : tagsNode) {
            if (tagNode != null && !tagNode.isNull() && StringUtils.hasText(tagNode.asText())) {
                tags.add(tagNode.asText());
            }
        }
        return tags;
    }

    private record HostWindowData(List<LoginSecurityLogEntity> failedLogins,
                                  List<AccountChangeLogEntity> recentActions,
                                  Map<String, AssetAccountRiskProfile> assetRiskProfiles) {
        private static HostWindowData empty() {
            return new HostWindowData(List.of(), List.of(), new HashMap<>());
        }
    }

    private record AssetAccountRiskProfile(String level,
                                           int score,
                                           Set<String> tags,
                                           String result,
                                           Boolean disabled) {
    }
}
