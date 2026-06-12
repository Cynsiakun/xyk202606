package com.cd.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaselineWindowsPathNormalizerTest {

    @Test
    void normalizeScriptRestoresSeceditPath() {
        String script = "secedit /export /cfg C:secedit.cfg; Select-String -Path C:secedit.cfg";

        String normalized = BaselineWindowsPathNormalizer.normalizeScript(script);

        assertEquals("secedit /export /cfg C:\\secedit.cfg; Select-String -Path C:\\secedit.cfg", normalized);
    }

    @Test
    void normalizeScriptRestoresRegistryPaths() {
        String script = "reg query 'HKLMSYSTEMCurrentControlSetControlLsa' /v 'RestrictAnonymous'";

        String normalized = BaselineWindowsPathNormalizer.normalizeScript(script);

        assertEquals("reg query 'HKLM\\SYSTEM\\CurrentControlSet\\Control\\Lsa' /v 'RestrictAnonymous'", normalized);
    }

    @Test
    void normalizeCheckKeyRestoresRegistryValuePath() {
        String checkKey = "HKEY_LOCAL_MACHINESOFTWAREMicrosoftWindows NTCurrentVersionWinlogonAutoAdminLogon";

        String normalized = BaselineWindowsPathNormalizer.normalizeCheckKey(checkKey);

        assertEquals("HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Winlogon\\AutoAdminLogon",
                normalized);
    }

    @Test
    void normalizeCheckKeySeparatesValueNameAfterNormalizedPath() {
        String checkKey = "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\WINEVT\\Logs\\System.evtxMaxSize";

        String normalized = BaselineWindowsPathNormalizer.normalizeCheckKey(checkKey);

        assertEquals("HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\WINEVT\\Logs\\System.evtx\\MaxSize",
                normalized);
    }
}
