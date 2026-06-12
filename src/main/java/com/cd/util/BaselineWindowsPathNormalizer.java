package com.cd.util;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BaselineWindowsPathNormalizer {

    private static final Map<String, String> REGISTRY_PATH_MAP = new LinkedHashMap<>();

    static {
        addRegistryPath("SYSTEMCurrentControlSetControlSecurePipeServersWinregAllowedExactPaths",
                "SYSTEM\\CurrentControlSet\\Control\\SecurePipeServers\\Winreg\\AllowedExactPaths");
        addRegistryPath("SYSTEMCurrentControlSetControlSecurePipeServersWinregAllowedPaths",
                "SYSTEM\\CurrentControlSet\\Control\\SecurePipeServers\\Winreg\\AllowedPaths");
        addRegistryPath("SYSTEMCurrentControlSetControlTerminal ServerWinStationsRDP-Tcp",
                "SYSTEM\\CurrentControlSet\\Control\\Terminal Server\\WinStations\\RDP-Tcp");
        addRegistryPath("SYSTEMCurrentControlSetControlLsa",
                "SYSTEM\\CurrentControlSet\\Control\\Lsa");
        addRegistryPath("SYSTEMCurrentControlSetServicesLanmanServerParameters",
                "SYSTEM\\CurrentControlSet\\Services\\LanmanServer\\Parameters");
        addRegistryPath("SYSTEMCurrentControlSetServicesTcpipParametersInterfaces",
                "SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters\\Interfaces");
        addRegistryPath("SYSTEMCurrentControlSetServicesTcpipParameters",
                "SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters");
        addRegistryPath("SYSTEMCurrentControlSetServicesSNMPParametersValidCommunities",
                "SYSTEM\\CurrentControlSet\\Services\\SNMP\\Parameters\\ValidCommunities");
        addRegistryPath("SYSTEMCurrentControlSetServicesNetlogonParameters",
                "SYSTEM\\CurrentControlSet\\Services\\Netlogon\\Parameters");
        addRegistryPath("SOFTWAREMicrosoftWindowsCurrentVersionWINEVTLogsApplication.evtx",
                "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\WINEVT\\Logs\\Application.evtx");
        addRegistryPath("SOFTWAREMicrosoftWindowsCurrentVersionWINEVTLogsSecurity.evtx",
                "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\WINEVT\\Logs\\Security.evtx");
        addRegistryPath("SOFTWAREMicrosoftWindowsCurrentVersionWINEVTLogsSystem.evtx",
                "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\WINEVT\\Logs\\System.evtx");
        addRegistryPath("SOFTWAREPoliciesMicrosoftWindowsWindowsUpdateAU",
                "SOFTWARE\\Policies\\Microsoft\\Windows\\WindowsUpdate\\AU");
        addRegistryPath("SOFTWAREPoliciesMicrosoftWindowsExplorer",
                "SOFTWARE\\Policies\\Microsoft\\Windows\\Explorer");
        addRegistryPath("SOFTWAREMicrosoftWindowsCurrentVersionPoliciesExplorer",
                "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer");
        addRegistryPath("SOFTWAREMicrosoftWindowsCurrentVersionPoliciesSystem",
                "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System");
        addRegistryPath("SOFTWAREMicrosoftWindows NTCurrentVersionWinlogon",
                "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Winlogon");
        addRegistryPath("Control PanelDesktop",
                "Control Panel\\Desktop");
    }

    private BaselineWindowsPathNormalizer() {
    }

    public static String normalizeScript(String script) {
        if (!StringUtils.hasText(script)) {
            return script;
        }
        String normalized = script.replace("C:secedit.cfg", "C:\\secedit.cfg");
        for (Map.Entry<String, String> entry : REGISTRY_PATH_MAP.entrySet()) {
            normalized = normalized.replace(entry.getKey(), entry.getValue());
        }
        return normalized;
    }

    public static String normalizeCheckKey(String checkKey) {
        if (!StringUtils.hasText(checkKey)) {
            return checkKey;
        }
        String normalized = checkKey;
        for (Map.Entry<String, String> entry : REGISTRY_PATH_MAP.entrySet()) {
            String badPath = entry.getKey();
            String goodPath = entry.getValue();
            if (normalized.equals(badPath)) {
                return goodPath;
            }
            if (normalized.startsWith(badPath) && !normalized.startsWith(goodPath + "\\")) {
                String suffix = normalized.substring(badPath.length());
                return goodPath + "\\" + suffix;
            }
            if (normalized.startsWith(goodPath) && !normalized.startsWith(goodPath + "\\")) {
                String suffix = normalized.substring(goodPath.length());
                if (StringUtils.hasText(suffix)) {
                    return goodPath + "\\" + suffix;
                }
            }
        }
        return normalized;
    }

    private static void addRegistryPath(String shortHivePath, String normalizedShortHivePath) {
        REGISTRY_PATH_MAP.put("HKEY_LOCAL_MACHINE" + shortHivePath,
                "HKEY_LOCAL_MACHINE\\" + normalizedShortHivePath);
        REGISTRY_PATH_MAP.put("HKLM" + shortHivePath, "HKLM\\" + normalizedShortHivePath);
        REGISTRY_PATH_MAP.put("HKEY_CURRENT_USER" + shortHivePath,
                "HKEY_CURRENT_USER\\" + normalizedShortHivePath);
        REGISTRY_PATH_MAP.put("HKCU" + shortHivePath, "HKCU\\" + normalizedShortHivePath);
    }
}
