package com.cd.util;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.springframework.util.StringUtils;

public final class VersionCompareUtil {

    private VersionCompareUtil() {
    }

    public static int compare(String v1, String v2) {
        if (!StringUtils.hasText(v1) || !StringUtils.hasText(v2)) {
            throw new IllegalArgumentException("Version value must not be blank");
        }
        return new ComparableVersion(clean(v1)).compareTo(new ComparableVersion(clean(v2)));
    }

    private static String clean(String version) {
        return version == null ? "" : version.trim();
    }
}
