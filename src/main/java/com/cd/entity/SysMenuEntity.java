package com.cd.entity;

import lombok.Data;

@Data
public class SysMenuEntity {

    private Long id;
    private String menuCode;
    private String menuName;
    private String menuPath;
    private String menuIcon;
    private Long permissionId;
    private Long parentId;
    private Integer sortOrder;
    private Integer status;
}
