package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TenantEntity {

    private Long id;
    private String name;
    private String contact;
    private Integer status;
    private String licenseEdition;
    private String licenseStatus;
    private LocalDateTime createdAt;
    private Long adminUserId;
    private String adminUserName;
}
