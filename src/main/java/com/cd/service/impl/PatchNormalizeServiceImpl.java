package com.cd.service.impl;

import com.cd.service.PatchNormalizeService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PatchNormalizeServiceImpl implements PatchNormalizeService {

    private static final Pattern KB_PATTERN = Pattern.compile("(?i)\\bKB[\\s\\-_:#]*([0-9]{6,8})\\b");
    private static final Pattern DIGITS_ONLY_PATTERN = Pattern.compile("^[0-9]{6,8}$");

    @Override
    public String normalizePatchId(String patchId) {
        if (!StringUtils.hasText(patchId)) {
            return null;
        }

        String normalized = patchId.trim();
        Matcher kbMatcher = KB_PATTERN.matcher(normalized);
        if (kbMatcher.find()) {
            return "KB" + kbMatcher.group(1);
        }

        String compact = normalized.replaceAll("[\\s\\-_:#]+", "");
        if (DIGITS_ONLY_PATTERN.matcher(compact).matches()) {
            return "KB" + compact;
        }

        return normalized
                .replaceAll("\\s+", " ")
                .trim()
                .toUpperCase(Locale.ROOT);
    }
}
