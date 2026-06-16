package com.cd.common.access;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AccessPolicyDefinition {

    private String key;
    private AccessDomain domain;
    private String featureCode;
    private AccessOperation operation;
    private String permissionCode;
    private String chain;
    private boolean menuPolicy;
}
