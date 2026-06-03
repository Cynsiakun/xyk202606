package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SysRoleResponseDTO {

    private Long id;
    private String roleCode;
    private String roleName;
    private Integer status;
    private LocalDateTime createAt;
    private List<Long> permissionIds;
}
