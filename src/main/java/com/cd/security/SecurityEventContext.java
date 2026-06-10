package com.cd.security;

import com.cd.entity.AccountChangeLogEntity;
import com.cd.entity.LoginSecurityLogEntity;
import com.cd.entity.WindowsEventLogEntity;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class SecurityEventContext {
    Long sourceLogId;
    WindowsEventLogEntity eventLog;
    LoginSecurityLogEntity loginLog;
    AccountChangeLogEntity accountLog;
    Map<String, String> eventData;
}
