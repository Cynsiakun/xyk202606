package com.cd.service.impl;

import com.cd.entity.AccountChangeLogEntity;
import com.cd.entity.LoginSecurityLogEntity;
import com.cd.entity.SecurityAlertEntity;
import com.cd.entity.WindowsEventLogEntity;
import com.cd.mapper.AccountMapper;
import com.cd.mapper.AccountChangeLogMapper;
import com.cd.mapper.LoginSecurityLogMapper;
import com.cd.mapper.SecurityAlertMapper;
import com.cd.security.SecurityEventContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityAlertRuleEngineImplTest {

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private LoginSecurityLogMapper loginSecurityLogMapper;

    @Mock
    private AccountChangeLogMapper accountChangeLogMapper;

    @Mock
    private SecurityAlertMapper securityAlertMapper;

    @InjectMocks
    private SecurityAlertRuleEngineImpl engine;

    @Test
    void shouldAggregateBruteForceIntoSingleAlertAtThreshold() {
        LocalDateTime base = LocalDateTime.of(2026, 6, 10, 10, 0, 0);
        List<LoginSecurityLogEntity> failedLogins = List.of(
                failLog(1L, base.plusMinutes(1)),
                failLog(2L, base.plusMinutes(2)),
                failLog(3L, base.plusMinutes(3)),
                failLog(4L, base.plusMinutes(4)),
                failLog(5L, base.plusMinutes(5))
        );

        when(loginSecurityLogMapper.selectFailedWithinWindow(eq(1L), any(), any())).thenReturn(failedLogins);
        when(accountChangeLogMapper.selectRecentActionsByHost(eq(1L), anyList(), any(), any())).thenReturn(List.of());
        when(accountMapper.selectLatestRiskSnapshotByHostIds(anyList())).thenReturn(List.of());
        when(securityAlertMapper.selectExistingDedupKeys(anyList(), any())).thenReturn(List.of());

        List<SecurityEventContext> contexts = List.of(
                failContext(1L, 1L, base.plusMinutes(1)),
                failContext(2L, 1L, base.plusMinutes(2)),
                failContext(3L, 1L, base.plusMinutes(3)),
                failContext(4L, 1L, base.plusMinutes(4)),
                failContext(5L, 1L, base.plusMinutes(5))
        );

        List<SecurityAlertEntity> alerts = engine.evaluate(contexts);

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getRuleCode()).isEqualTo("BRUTE_FORCE_LOGIN");
        assertThat(alerts.get(0).getLevel()).isEqualTo("High");
    }

    @Test
    void shouldRaiseQuickLoginAlertForNewAccount() {
        LocalDateTime base = LocalDateTime.of(2026, 6, 10, 11, 0, 0);
        AccountChangeLogEntity createAction = new AccountChangeLogEntity();
        createAction.setHostId(1L);
        createAction.setEventId(4720);
        createAction.setEventTime(base.minusMinutes(10));
        createAction.setTargetUsername("test");
        createAction.setActionType("create");
        createAction.setOperatorUsername("Administrator");

        when(loginSecurityLogMapper.selectFailedWithinWindow(eq(1L), any(), any())).thenReturn(List.of());
        when(accountChangeLogMapper.selectRecentActionsByHost(eq(1L), anyList(), any(), any())).thenReturn(List.of(createAction));
        when(accountMapper.selectLatestRiskSnapshotByHostIds(anyList())).thenReturn(List.of());
        when(securityAlertMapper.selectExistingDedupKeys(anyList(), any())).thenReturn(List.of());

        List<SecurityAlertEntity> alerts = engine.evaluate(List.of(successContext(10L, 1L, base, "test")));

        assertThat(alerts).extracting(SecurityAlertEntity::getRuleCode)
                .contains("NEW_ACCOUNT_QUICK_LOGIN");
        assertThat(alerts).filteredOn(alert -> "NEW_ACCOUNT_QUICK_LOGIN".equals(alert.getRuleCode()))
                .singleElement()
                .extracting(SecurityAlertEntity::getLevel)
                .isEqualTo("Critical");
    }

    @Test
    void shouldSuppressAlertWhenExistingNewAlertHasSameDedupKey() {
        LocalDateTime base = LocalDateTime.of(2026, 6, 10, 23, 30, 0);
        when(loginSecurityLogMapper.selectFailedWithinWindow(eq(1L), any(), any())).thenReturn(List.of());
        when(accountChangeLogMapper.selectRecentActionsByHost(eq(1L), anyList(), any(), any())).thenReturn(List.of());
        when(accountMapper.selectLatestRiskSnapshotByHostIds(anyList())).thenReturn(List.of());
        when(securityAlertMapper.selectExistingDedupKeys(anyList(), any()))
                .thenReturn(List.of("1|OFF_HOURS_LOGIN|administrator|10.0.0.1"));

        List<SecurityAlertEntity> alerts = engine.evaluate(List.of(successContext(20L, 1L, base, "Administrator")));

        assertThat(alerts).extracting(SecurityAlertEntity::getRuleCode)
                .doesNotContain("OFF_HOURS_LOGIN")
                .contains("RISKY_ACCOUNT_LOGIN");
    }

    @Test
    void shouldNotTreatMediumAssetRiskAsDangerousLoginByItself() {
        LocalDateTime base = LocalDateTime.of(2026, 6, 10, 9, 30, 0);
        String mediumRiskAssetJson = """
                [
                  {
                    "name":"xieya",
                    "risk_level":"MEDIUM",
                    "risk_score":55,
                    "risk_tags":["非标准账号","启用状态"],
                    "result":"当前数据不足以判断其权限范围或业务必要性。"
                  }
                ]
                """;

        com.cd.dto.AccountRiskSnapshotDTO snapshot = new com.cd.dto.AccountRiskSnapshotDTO();
        snapshot.setHostId(1L);
        snapshot.setAccountId(100L);
        snapshot.setMacAddress("CC:5E:F8:A1:31:B4");
        snapshot.setAssetJson(mediumRiskAssetJson);

        when(loginSecurityLogMapper.selectFailedWithinWindow(eq(1L), any(), any())).thenReturn(List.of());
        when(accountChangeLogMapper.selectRecentActionsByHost(eq(1L), anyList(), any(), any())).thenReturn(List.of());
        when(accountMapper.selectLatestRiskSnapshotByHostIds(anyList())).thenReturn(List.of(snapshot));

        List<SecurityAlertEntity> alerts = engine.evaluate(List.of(successContext(30L, 1L, base, "xieya")));

        assertThat(alerts).extracting(SecurityAlertEntity::getRuleCode)
                .doesNotContain("RISKY_ACCOUNT_LOGIN");
    }

    private SecurityEventContext failContext(Long sourceLogId, Long hostId, LocalDateTime eventTime) {
        WindowsEventLogEntity eventLog = new WindowsEventLogEntity();
        eventLog.setHostId(hostId);
        eventLog.setEventId(4625);
        eventLog.setEventTime(eventTime);

        LoginSecurityLogEntity loginLog = failLog(sourceLogId, eventTime);

        return SecurityEventContext.builder()
                .sourceLogId(sourceLogId)
                .eventLog(eventLog)
                .loginLog(loginLog)
                .eventData(Map.of())
                .build();
    }

    private SecurityEventContext successContext(Long sourceLogId, Long hostId, LocalDateTime eventTime, String username) {
        WindowsEventLogEntity eventLog = new WindowsEventLogEntity();
        eventLog.setHostId(hostId);
        eventLog.setEventId(4624);
        eventLog.setEventTime(eventTime);

        LoginSecurityLogEntity loginLog = new LoginSecurityLogEntity();
        loginLog.setHostId(hostId);
        loginLog.setEventId(4624);
        loginLog.setEventTime(eventTime);
        loginLog.setUsername(username);
        loginLog.setLoginResult("success");
        loginLog.setSourceIp("10.0.0.1");
        loginLog.setLoginType(10);

        return SecurityEventContext.builder()
                .sourceLogId(sourceLogId)
                .eventLog(eventLog)
                .loginLog(loginLog)
                .eventData(Map.of())
                .build();
    }

    private LoginSecurityLogEntity failLog(Long sourceLogId, LocalDateTime eventTime) {
        LoginSecurityLogEntity loginLog = new LoginSecurityLogEntity();
        loginLog.setSourceLogId(sourceLogId);
        loginLog.setHostId(1L);
        loginLog.setEventId(4625);
        loginLog.setEventTime(eventTime);
        loginLog.setUsername("Administrator");
        loginLog.setLoginResult("fail");
        loginLog.setSourceIp("10.0.0.1");
        return loginLog;
    }
}
