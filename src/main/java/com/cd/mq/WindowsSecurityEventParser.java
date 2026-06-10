package com.cd.mq;

import com.cd.entity.AccountChangeLogEntity;
import com.cd.entity.LoginSecurityLogEntity;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Windows 安全事件分流解析器。
 */
public final class WindowsSecurityEventParser {

    public static final Set<Integer> LOGIN_EVENT_IDS = Set.of(4624, 4625, 4634, 4647);
    public static final Set<Integer> ACCOUNT_EVENT_IDS = Set.of(4720, 4722, 4723, 4724, 4726, 4728, 4738);

    private static final Pattern DATA_PATTERN = Pattern.compile(
            "<Data\\s+Name=['\"]([^'\"]+)['\"]\\s*(?:/>|>(.*?)</Data>)", Pattern.DOTALL);

    private WindowsSecurityEventParser() {
    }

    public static boolean isLoginEvent(int eventId) {
        return LOGIN_EVENT_IDS.contains(eventId);
    }

    public static boolean isAccountEvent(int eventId) {
        return ACCOUNT_EVENT_IDS.contains(eventId);
    }

    public static LoginSecurityLogEntity parseLogin(int eventId, LocalDateTime eventTime, Long hostId,
                                                    String fallbackUsername, String rawXml) {
        Map<String, String> data = extractEventData(rawXml);

        LoginSecurityLogEntity entity = new LoginSecurityLogEntity();
        entity.setHostId(hostId);
        entity.setEventId(eventId);
        entity.setEventTime(eventTime);
        entity.setUsername(resolveUsername(data, "TargetDomainName", "TargetUserName", fallbackUsername));
        entity.setLoginResult(loginResult(eventId));
        entity.setLoginType(toInt(clean(data.get("LogonType"))));
        entity.setSourceIp(clean(data.get("IpAddress")));
        entity.setProcessName(clean(data.get("ProcessName")));
        entity.setIsElevated("%%1842".equals(clean(data.get("ElevatedToken"))) ? 1 : 0);
        return entity;
    }

    public static AccountChangeLogEntity parseAccount(int eventId, LocalDateTime eventTime, Long hostId,
                                                      String rawXml) {
        Map<String, String> data = extractEventData(rawXml);

        AccountChangeLogEntity entity = new AccountChangeLogEntity();
        entity.setHostId(hostId);
        entity.setEventId(eventId);
        entity.setEventTime(eventTime);
        entity.setOperatorUsername(resolveUsername(data, "SubjectDomainName", "SubjectUserName", null));

        String target = clean(data.get("TargetUserName"));
        if (eventId == 4728 && StringUtils.hasText(clean(data.get("MemberName")))) {
            target = clean(data.get("MemberName"));
        }
        entity.setTargetUsername(target);
        entity.setActionType(accountActionType(eventId));
        entity.setDetails(buildDetails(eventId, data));
        return entity;
    }

    public static Map<String, String> extractEventData(String rawXml) {
        Map<String, String> map = new HashMap<>();
        if (!StringUtils.hasText(rawXml)) {
            return map;
        }
        Matcher matcher = DATA_PATTERN.matcher(rawXml);
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = matcher.group(2);
            map.put(name, value == null ? null : unescapeXml(value));
        }
        return map;
    }

    private static String loginResult(int eventId) {
        return switch (eventId) {
            case 4624 -> "success";
            case 4625 -> "fail";
            case 4634, 4647 -> "logout";
            default -> null;
        };
    }

    private static String accountActionType(int eventId) {
        return switch (eventId) {
            case 4720 -> "create";
            case 4722 -> "enable";
            case 4723 -> "change_password";
            case 4724 -> "reset_password";
            case 4726 -> "delete";
            case 4728 -> "add_to_group";
            case 4738 -> "modify";
            default -> "unknown";
        };
    }

    private static String buildDetails(int eventId, Map<String, String> data) {
        StringBuilder builder = new StringBuilder();
        String groupName = clean(data.get("TargetUserName"));
        switch (eventId) {
            case 4728 -> builder.append("成员 ")
                    .append(orDash(clean(data.get("MemberName"))))
                    .append(" 加入组 ")
                    .append(orDash(groupName));
            case 4738 -> {
                builder.append("账户属性被修改");
                appendIf(builder, "SamAccountName", clean(data.get("SamAccountName")));
                appendIf(builder, "DisplayName", clean(data.get("DisplayName")));
                appendIf(builder, "UserAccountControl", clean(data.get("UserAccountControl")));
            }
            case 4723 -> builder.append("尝试修改账户密码");
            case 4724 -> builder.append("尝试重置账户密码");
            case 4720 -> builder.append("创建用户");
            case 4722 -> builder.append("启用用户");
            case 4726 -> builder.append("删除用户");
            default -> builder.append("账户变更事件 ").append(eventId);
        }
        String details = builder.toString();
        return details.length() > 1000 ? details.substring(0, 1000) : details;
    }

    private static void appendIf(StringBuilder builder, String label, String value) {
        if (StringUtils.hasText(value)) {
            builder.append("; ").append(label).append('=').append(value);
        }
    }

    private static String orDash(String value) {
        return StringUtils.hasText(value) ? value : "-";
    }

    private static String resolveUsername(Map<String, String> data, String domainKey, String userKey,
                                          String fallback) {
        String user = clean(data.get(userKey));
        String domain = clean(data.get(domainKey));
        if (!StringUtils.hasText(user)) {
            return fallback;
        }
        String full = StringUtils.hasText(domain) ? domain + "\\" + user : user;
        return full.length() > 255 ? full.substring(0, 255) : full;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || "-".equals(trimmed) ? null : trimmed;
    }

    private static Integer toInt(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String unescapeXml(String value) {
        if (value.indexOf('&') < 0) {
            return value;
        }
        return value.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }
}
