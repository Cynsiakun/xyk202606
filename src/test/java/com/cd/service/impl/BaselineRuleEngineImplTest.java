package com.cd.service.impl;

import com.cd.entity.BaselineRuleItemEntity;
import com.cd.entity.BaselineResultEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BaselineRuleEngineImplTest {

    private final BaselineRuleEngineImpl engine = new BaselineRuleEngineImpl(null, null, null, null);

    @Test
    void shouldCompareNumericOutputUsingExtractedNumberWhenExpectedIsNumeric() {
        BaselineRuleItemEntity item = new BaselineRuleItemEntity();
        item.setOperator("<=");
        item.setExpectedValue("90");
        item.setMatchType("EXACT");

        boolean result = (boolean) ReflectionTestUtils.invokeMethod(
                engine,
                "compare",
                item,
                "Maximum password age (days):                          42"
        );

        assertThat(result).isTrue();
    }

    @Test
    void shouldTreatMissingRegistryValueAsComparableFact() {
        boolean missing = (boolean) ReflectionTestUtils.invokeMethod(
                engine,
                "isMissingRegistryValue",
                "注册表路径或值不存在：HKEY_LOCAL_MACHINE\\A\\B（[WinError 2] 系统找不到指定的文件。）"
        );

        assertThat(missing).isTrue();
    }

    @Test
    void shouldCompareTextIgnoringCase() {
        BaselineRuleItemEntity item = new BaselineRuleItemEntity();
        item.setOperator("=");
        item.setExpectedValue("Running");
        item.setMatchType("EXACT");

        boolean result = (boolean) ReflectionTestUtils.invokeMethod(engine, "compare", item, "running");

        assertThat(result).isTrue();
    }

    @Test
    void shouldCleanCommandNoiseFromEvidence() {
        String raw = """
                The task has completed successfully.
                See log %windir%\\security\\logs\\scesrv.log for detail info.

                C:\\secedit.cfg:111:SeShutdownPrivilege = *S-1-5-32-544,*S-1-5-32-545
                """;

        String cleaned = (String) ReflectionTestUtils.invokeMethod(engine, "cleanEvidence", raw, null);

        assertThat(cleaned).isEqualTo("SeShutdownPrivilege = *S-1-5-32-544,*S-1-5-32-545");
    }

    @Test
    void shouldBuildReadableMessageFromComparisonResult() {
        BaselineRuleItemEntity item = new BaselineRuleItemEntity();
        item.setOperator(">=");
        item.setExpectedValue("14");
        item.setMatchType("EXACT");

        Object result = ReflectionTestUtils.invokeMethod(engine, "evaluateItem", item, "90", "90");

        assertThat(ReflectionTestUtils.getField(result, "status")).isEqualTo("PASS");
        assertThat(ReflectionTestUtils.getField(result, "message").toString()).contains("符合要求").contains("高于或等于要求值");
    }

    @Test
    void shouldCompareEnumValueSetFromJson() {
        BaselineRuleItemEntity item = new BaselineRuleItemEntity();
        item.setOperator("IN");
        item.setValueType("ENUM");
        item.setValueSet("[\"Success\",\"SuccessAndFailure\"]");

        boolean result = (boolean) ReflectionTestUtils.invokeMethod(engine, "compare", item, "successandfailure");

        assertThat(result).isTrue();
    }

    @Test
    void shouldCompareBooleanAliases() {
        BaselineRuleItemEntity item = new BaselineRuleItemEntity();
        item.setOperator("=");
        item.setExpectedValue("true");
        item.setValueType("BOOLEAN");

        boolean result = (boolean) ReflectionTestUtils.invokeMethod(engine, "compare", item, "enabled");

        assertThat(result).isTrue();
    }

    @Test
    void shouldBuildResultWithFallbackItemAndDimensions() throws Exception {
        BaselineRuleItemEntity item = new BaselineRuleItemEntity();
        item.setId(55L);
        item.setRuleId(7L);
        item.setCheckKey("default_password_lifetime");
        item.setOperator("<=");
        item.setExpectedValue("90");
        item.setValueType("NUMBER");
        item.setProtectionLevelId(3L);

        JsonNode resultJson = new ObjectMapper().readTree("""
                {
                  "ruleId": 7,
                  "ruleVersion": 2,
                  "checkKey": "default_password_lifetime",
                  "executeStatus": "SUCCESS",
                  "actualValue": "60",
                  "evidence": "SHOW VARIABLES => 60"
                }
                """);

        BaselineResultEntity result = ReflectionTestUtils.invokeMethod(
                engine,
                "buildResult",
                resultJson,
                100L,
                200L,
                300L,
                0L,
                3L,
                Map.of(),
                Map.of("7\ndefault_password_lifetime", item),
                Map.of(7L, 6L),
                LocalDateTime.parse("2026-06-17T23:00:00"),
                LocalDateTime.parse("2026-06-17T23:01:00")
        );

        assertThat(result.getStatus()).isEqualTo("PASS");
        assertThat(result.getItemId()).isEqualTo(55L);
        assertThat(result.getProtectionLevelId()).isEqualTo(3L);
        assertThat(result.getAssetTypeId()).isEqualTo(6L);
    }
}
