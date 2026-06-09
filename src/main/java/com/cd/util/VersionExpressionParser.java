package com.cd.util;

import com.cd.dto.AssetInfoDTO;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class VersionExpressionParser {

    private VersionExpressionParser() {
    }

    public static boolean matches(String matchType, String expression, AssetInfoDTO assetInfo) {
        if (!StringUtils.hasText(matchType)) {
            return true;
        }
        if (!StringUtils.hasText(expression)) {
            return true;
        }

        String type = matchType.trim().toLowerCase(Locale.ROOT);
        String expr = expression.trim();
        return switch (type) {
            case "exact" -> exact(expr, textCandidates(assetInfo));
            case "contains" -> contains(expr, textCandidates(assetInfo));
            case "version_lt" -> compareVersion(assetInfo.getVersion(), stripOperator(expr), "<");
            case "version_le" -> compareVersion(assetInfo.getVersion(), stripOperator(expr), "<=");
            case "version_gt" -> compareVersion(assetInfo.getVersion(), stripOperator(expr), ">");
            case "version_ge" -> compareVersion(assetInfo.getVersion(), stripOperator(expr), ">=");
            case "version_range" -> versionRange(assetInfo.getVersion(), expr);
            case "regex" -> regex(expr, textCandidates(assetInfo));
            default -> false;
        };
    }

    public static String reason(String matchType) {
        if (!StringUtils.hasText(matchType)) {
            return "资产名称命中漏洞规则";
        }
        return switch (matchType.trim().toLowerCase(Locale.ROOT)) {
            case "exact" -> "资产实际值与规则表达式完全一致";
            case "contains" -> "资产实际值包含规则表达式";
            case "version_lt" -> "资产版本低于安全版本要求";
            case "version_le" -> "资产版本不高于规则限定版本";
            case "version_gt" -> "资产版本高于规则限定版本";
            case "version_ge" -> "资产版本不低于规则限定版本";
            case "version_range" -> "资产版本落入漏洞影响区间";
            case "regex" -> "资产实际值命中正则表达式";
            default -> "资产命中漏洞规则";
        };
    }

    private static boolean exact(String expr, List<String> candidates) {
        for (String candidate : candidates) {
            if (candidate.equals(expr)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(String expr, List<String> candidates) {
        String normalizedExpr = expr.toLowerCase(Locale.ROOT);
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).contains(normalizedExpr)) {
                return true;
            }
        }
        return false;
    }

    private static boolean regex(String expr, List<String> candidates) {
        Pattern pattern = Pattern.compile(expr);
        for (String candidate : candidates) {
            if (pattern.matcher(candidate).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean versionRange(String assetVersion, String expression) {
        if (!StringUtils.hasText(assetVersion)) {
            return false;
        }
        String[] parts = expression.split(",");
        for (String rawPart : parts) {
            String part = rawPart.trim();
            if (!StringUtils.hasText(part)) {
                continue;
            }
            String operator = resolveOperator(part);
            if (!compareVersion(assetVersion, stripOperator(part), operator)) {
                return false;
            }
        }
        return true;
    }

    private static boolean compareVersion(String assetVersion, String ruleVersion, String operator) {
        if (!StringUtils.hasText(assetVersion) || !StringUtils.hasText(ruleVersion)) {
            return false;
        }
        int compare = VersionCompareUtil.compare(assetVersion, ruleVersion);
        return switch (operator) {
            case "<" -> compare < 0;
            case "<=" -> compare <= 0;
            case ">" -> compare > 0;
            case ">=" -> compare >= 0;
            case "=" -> compare == 0;
            default -> false;
        };
    }

    private static String resolveOperator(String expression) {
        String expr = expression.trim();
        if (expr.startsWith("<=")) {
            return "<=";
        }
        if (expr.startsWith(">=")) {
            return ">=";
        }
        if (expr.startsWith("<")) {
            return "<";
        }
        if (expr.startsWith(">")) {
            return ">";
        }
        if (expr.startsWith("=")) {
            return "=";
        }
        return "=";
    }

    private static String stripOperator(String expression) {
        String expr = expression == null ? "" : expression.trim();
        if (expr.startsWith("<=") || expr.startsWith(">=")) {
            return expr.substring(2).trim();
        }
        if (expr.startsWith("<") || expr.startsWith(">") || expr.startsWith("=")) {
            return expr.substring(1).trim();
        }
        return expr;
    }

    private static List<String> textCandidates(AssetInfoDTO assetInfo) {
        List<String> candidates = new ArrayList<>();
        add(candidates, assetInfo.getVersion());
        add(candidates, assetInfo.getCommand());
        add(candidates, assetInfo.getName());
        String joined = String.join(" ", candidates).trim();
        add(candidates, joined);
        return candidates;
    }

    private static void add(List<String> candidates, String value) {
        if (StringUtils.hasText(value) && !candidates.contains(value.trim())) {
            candidates.add(value.trim());
        }
    }
}
