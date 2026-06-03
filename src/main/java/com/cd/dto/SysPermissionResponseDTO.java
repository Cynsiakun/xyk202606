package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysPermissionResponseDTO {

    private Long id;
    private String permissionCode;
    private String permissionName;
    private String permissionType;
    private String path;
    private Integer status;
    private LocalDateTime createAt;
}
