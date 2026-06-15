package com.cd.service.impl;

import com.cd.entity.BaselineRuleItemEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
}
