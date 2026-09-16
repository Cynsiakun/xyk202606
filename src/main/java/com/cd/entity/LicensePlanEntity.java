package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LicensePlanEntity {

    private Long id;
    private String code;
    private String name;
    private Integer userLimit;
    private Integer hostLimit;
    private String featureFlags;
    private Integer status;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
