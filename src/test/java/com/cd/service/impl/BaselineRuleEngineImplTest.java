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
}
