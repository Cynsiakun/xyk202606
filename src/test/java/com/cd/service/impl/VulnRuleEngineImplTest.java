package com.cd.service.impl;

import com.cd.entity.AppEntity;
import com.cd.entity.HostEntity;
import com.cd.entity.HostVulnResultEntity;
import com.cd.entity.VulnRuleEntity;
import com.cd.mapper.AppMapper;
import com.cd.mapper.HostMapper;
import com.cd.mapper.HostVulnResultMapper;
import com.cd.mapper.ProcessMapper;
import com.cd.mapper.ServiceMapper;
import com.cd.service.RuleEnrichmentService;
import com.cd.service.VulnRuleCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VulnRuleEngineImplTest {

    @Mock
    private HostMapper hostMapper;

    @Mock
    private AppMapper appMapper;

    @Mock
    private ServiceMapper serviceMapper;

    @Mock
    private ProcessMapper processMapper;

    @Mock
    private HostVulnResultMapper hostVulnResultMapper;

    @Mock
    private VulnRuleCacheService vulnRuleCacheService;

    @Mock
    private RuleEnrichmentService ruleEnrichmentService;

    @InjectMocks
    private VulnRuleEngineImpl engine;

    @BeforeEach
    void setUp() {
        engine = new VulnRuleEngineImpl(
                hostMapper,
                appMapper,
                serviceMapper,
                processMapper,
                hostVulnResultMapper,
                vulnRuleCacheService,
                ruleEnrichmentService,
                new ObjectMapper()
        );
    }

    @Test
    void shouldFallbackToLatestNonEmptyAssetSnapshotWhenNewestSnapshotIsEmpty() {
        HostEntity host = new HostEntity();
        host.setId(1L);
        host.setTenantId(9905L);
        host.setMacAddress("00:0C:29:15:8D:66");
        host.setOsName("Ubuntu");
        host.setOsVersion("22.04");
        host.setOsRelease("22.04");
        when(hostMapper.selectByIdAndTenant(1L, 9905L)).thenReturn(host);

        AppEntity latestEmpty = new AppEntity();
        latestEmpty.setAssetCount(0);
        latestEmpty.setAssetJson("[]");
        when(appMapper.selectLatestByMacAndTenant(host.getMacAddress(), 9905L)).thenReturn(latestEmpty);

        AppEntity previousNonEmpty = new AppEntity();
        previousNonEmpty.setAssetCount(1);
        previousNonEmpty.setAssetJson("""
                [{"name":"openssl","version":"1.1.1f","displayName":"OpenSSL"}]
                """);
        when(appMapper.selectLatestNonEmptyByMacAndTenant(host.getMacAddress(), 9905L)).thenReturn(previousNonEmpty);

        when(serviceMapper.selectLatestByMacAndTenant(host.getMacAddress(), 9905L)).thenReturn(null);
        when(serviceMapper.selectLatestNonEmptyByMacAndTenant(host.getMacAddress(), 9905L)).thenReturn(null);
        when(processMapper.selectLatestByMacAndTenant(host.getMacAddress(), 9905L)).thenReturn(null);
        when(processMapper.selectLatestNonEmptyByMacAndTenant(host.getMacAddress(), 9905L)).thenReturn(null);

        VulnRuleEntity rule = new VulnRuleEntity();
        rule.setId(258L);
        rule.setProductType("app");
        rule.setProductName("openssl");
        rule.setMatchType("version_lt");
        rule.setAffectedVersionExpr("<3.0.0");
        rule.setSeverity("HIGH");
        rule.setTitle("OpenSSL vulnerable");
        rule.setSuggestion("Upgrade");

        when(vulnRuleCacheService.getRuleCache()).thenReturn(Map.of("app", List.of(rule)));
        when(vulnRuleCacheService.getRulesByType("os")).thenReturn(List.of());
        when(vulnRuleCacheService.getRulesByType("app")).thenReturn(List.of(rule));
        when(ruleEnrichmentService.enrich(any(HostVulnResultEntity.class), eq(rule), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<HostVulnResultEntity> results = engine.evaluateHostForTenant(1L, 9905L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRuleId()).isEqualTo(258L);
        assertThat(results.get(0).getProductName()).contains("openssl");
        verify(hostVulnResultMapper).markInactiveByHostIdAndTenant(1L, 9905L);
        verify(appMapper).selectLatestNonEmptyByMacAndTenant(host.getMacAddress(), 9905L);

        ArgumentCaptor<HostVulnResultEntity> captor = ArgumentCaptor.forClass(HostVulnResultEntity.class);
        verify(hostVulnResultMapper).insert(captor.capture());
        assertThat(captor.getValue().getProductVersion()).isEqualTo("1.1.1f");
    }

    @Test
    void shouldNotFallbackWhenNewestSnapshotAlreadyContainsAssets() {
        HostEntity host = new HostEntity();
        host.setId(2L);
        host.setTenantId(9905L);
        host.setMacAddress("00:0C:29:AA:BB:CC");
        host.setOsName("Ubuntu");
        host.setOsVersion("22.04");
        host.setOsRelease("22.04");
        when(hostMapper.selectByIdAndTenant(2L, 9905L)).thenReturn(host);

        AppEntity latestNonEmpty = new AppEntity();
        latestNonEmpty.setAssetCount(1);
        latestNonEmpty.setAssetJson("""
                [{"name":"openssl","version":"1.1.1f"}]
                """);
        when(appMapper.selectLatestByMacAndTenant(host.getMacAddress(), 9905L)).thenReturn(latestNonEmpty);

        when(serviceMapper.selectLatestByMacAndTenant(host.getMacAddress(), 9905L)).thenReturn(null);
        when(serviceMapper.selectLatestNonEmptyByMacAndTenant(host.getMacAddress(), 9905L)).thenReturn(null);
        when(processMapper.selectLatestByMacAndTenant(host.getMacAddress(), 9905L)).thenReturn(null);
        when(processMapper.selectLatestNonEmptyByMacAndTenant(host.getMacAddress(), 9905L)).thenReturn(null);

        VulnRuleEntity rule = new VulnRuleEntity();
        rule.setId(258L);
        rule.setProductType("app");
        rule.setProductName("openssl");
        rule.setMatchType("version_lt");
        rule.setAffectedVersionExpr("<3.0.0");
        rule.setSeverity("HIGH");
        rule.setTitle("OpenSSL vulnerable");

        when(vulnRuleCacheService.getRuleCache()).thenReturn(Map.of("app", List.of(rule)));
        when(vulnRuleCacheService.getRulesByType("os")).thenReturn(List.of());
        when(vulnRuleCacheService.getRulesByType("app")).thenReturn(List.of(rule));
        when(ruleEnrichmentService.enrich(any(HostVulnResultEntity.class), eq(rule), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<HostVulnResultEntity> results = engine.evaluateHostForTenant(2L, 9905L);

        assertThat(results).hasSize(1);
        verify(appMapper, never()).selectLatestNonEmptyByMacAndTenant(host.getMacAddress(), 9905L);
    }
}
