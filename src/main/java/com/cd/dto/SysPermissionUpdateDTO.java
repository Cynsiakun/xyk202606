package com.cd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SysPermissionUpdateDTO {

    @NotBlank(message = "权限编码不能为空")
    @Size(max = 100, message = "权限编码长度不能超过100")
    private String permissionCode;

    @NotBlank(message = "权限名称不能为空")
    @Size(max = 100, message = "权限名称长度不能超过100")
    private String permissionName;

    @NotBlank(message = "权限类型不能为空")
    @Size(max = 20, message = "权限类型长度不能超过20")
    private String permissionType;

    @Size(max = 255, message = "路径长度不能超过255")
    private String path;

    @NotNull(message = "状态不能为空")
    private Integer status;
}
