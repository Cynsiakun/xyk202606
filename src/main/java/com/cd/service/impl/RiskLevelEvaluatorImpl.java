package com.cd.service.impl;

import com.cd.dto.RiskContext;
import com.cd.entity.HostPatchRiskEntity;
import com.cd.service.RiskLevelEvaluator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class RiskLevelEvaluatorImpl implements RiskLevelEvaluator {

    private static final String LEVEL_CRITICAL = "critical";
    private static final String LEVEL_HIGH = "high";
    private static final String LEVEL_MEDIUM = "medium";
    private static final String LEVEL_LOW = "low";
    private static final String STATUS_OPEN = "open";

    @Override
    public List<HostPatchRiskEntity> evaluate(List<RiskContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return List.of();
        }

        List<HostPatchRiskEntity> risks = new ArrayList<>();
        for (RiskContext context : contexts) {
            HostPatchRiskEntity risk = new HostPatchRiskEntity();
            risk.setHostId(context.getHostId());
            risk.setRiskId(buildRiskId(context));
            risk.setRiskType(context.getRiskType());
            risk.setRiskName(context.getRiskName());
            risk.setRiskLevel(evaluateLevel(context));
            risk.setRelatedPatchId(context.getRelatedPatchId());
            risk.setRelatedCve(context.getRelatedCve());
            risk.setEvidence(context.getEvidence());
            risk.setRecommendation(context.getRecommendation());
            risk.setStatus(STATUS_OPEN);
            risk.setScanTime(LocalDateTime.now());
            risks.add(risk);
        }
        return risks;
    }

    private String evaluateLevel(RiskContext context) {
        RiskLevel level = RiskLevel.LOW;

        if (Integer.valueOf(1).equals(context.getKevFlag())) {
            level = level.max(RiskLevel.CRITICAL);
        }
        if ("active".equalsIgnoreCase(context.getExploitStatus())) {
            level = level.max(RiskLevel.CRITICAL);
        }
        if ("eol".equalsIgnoreCase(context.getSupportStatus())) {
            level = level.max(RiskLevel.CRITICAL);
        }
        level = level.max(levelFromCvss(context.getCvssScore()));
        level = level.max(levelFromIssueSeverity(context.getIssueSeverity()));

        if (Boolean.TRUE.equals(context.getPendingReboot())) {
            level = level.max(RiskLevel.MEDIUM);
        }
        if (isInstallFailure(context.getInstallStatus())) {
            level = level.max(RiskLevel.HIGH);
        }

        return level.value;
    }

    private RiskLevel levelFromCvss(BigDecimal cvssScore) {
        if (cvssScore == null) {
            return RiskLevel.LOW;
        }
        if (cvssScore.compareTo(BigDecimal.valueOf(9.0)) >= 0) {
            return RiskLevel.CRITICAL;
        }
        if (cvssScore.compareTo(BigDecimal.valueOf(7.0)) >= 0) {
            return RiskLevel.HIGH;
        }
        if (cvssScore.compareTo(BigDecimal.valueOf(4.0)) >= 0) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private RiskLevel levelFromIssueSeverity(String issueSeverity) {
        if (!StringUtils.hasText(issueSeverity)) {
            return RiskLevel.LOW;
        }
        return switch (issueSeverity.trim().toLowerCase(Locale.ROOT)) {
            case LEVEL_HIGH -> RiskLevel.HIGH;
            case LEVEL_MEDIUM -> RiskLevel.MEDIUM;
            case LEVEL_LOW -> RiskLevel.LOW;
            default -> RiskLevel.LOW;
        };
    }

    private boolean isInstallFailure(String installStatus) {
        if (!StringUtils.hasText(installStatus)) {
            return false;
        }
        String normalized = installStatus.trim().toLowerCase(Locale.ROOT);
        return "failed".equals(normalized) || "partial".equals(normalized);
    }

    private String buildRiskId(RiskContext context) {
        String key = String.join("|",
                String.valueOf(context.getHostId()),
                value(context.getRiskType()),
                value(context.getRiskName()),
                value(context.getRelatedPatchId()),
                value(context.getRelatedCve()));
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String value(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private enum RiskLevel {
        LOW(1, LEVEL_LOW),
        MEDIUM(2, LEVEL_MEDIUM),
        HIGH(3, LEVEL_HIGH),
        CRITICAL(4, LEVEL_CRITICAL);

        private final int score;
        private final String value;

        RiskLevel(int score, String value) {
            this.score = score;
            this.value = value;
        }

        private RiskLevel max(RiskLevel other) {
            return other.score > score ? other : this;
        }
    }
}
