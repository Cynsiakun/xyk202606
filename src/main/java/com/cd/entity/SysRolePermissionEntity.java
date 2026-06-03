package com.cd.entity;

import lombok.Data;

@Data
public class SysRolePermissionEntity {

    private Long id;
    private Long roleId;
    private Long permissionId;
}
