package com.cd.common.access;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AccessDecision {

    private boolean allowed;
    private String reasonCode;
    private String message;
    private String policyKey;

    public static AccessDecision allow(String policyKey) {
        return new AccessDecision(true, "ALLOW", "Access allowed", policyKey);
    }

    public static AccessDecision deny(String policyKey, String reasonCode, String message) {
        return new AccessDecision(false, reasonCode, message, policyKey);
    }
}
