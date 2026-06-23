package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BaselineAssetTypeEntity {

    private Long id;
    private String typeCode;
    private String typeName;
    private String category;
    private Integer sortOrder;
    private Integer enabled;
    private LocalDateTime createTime;
}
