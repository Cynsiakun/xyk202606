package com.cd.util;

import com.cd.dto.AssetInfoDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionExpressionParserTest {

    @Test
    void comparableVersionHandlesNumericOrdering() {
        assertTrue(VersionCompareUtil.compare("1.10.0", "1.9.0") > 0);
        assertEquals(0, VersionCompareUtil.compare("1.0.0", "1.0"));
    }

    @Test
    void versionRangeSupportsCommaSeparatedBounds() {
        AssetInfoDTO asset = AssetInfoDTO.builder()
                .type("app")
                .name("nginx")
                .version("5.0.13")
                .build();

        assertTrue(VersionExpressionParser.matches("version_range", ">=1.0,<5.0.14", asset));
        assertFalse(VersionExpressionParser.matches("version_range", ">=1.0,<5.0.13", asset));
    }
}
