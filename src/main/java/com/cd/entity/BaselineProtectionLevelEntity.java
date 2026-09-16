package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BaselineProtectionLevelEntity {

    private Long id;
    private String levelCode;
    private String levelName;
    private Integer levelOrder;
    private String description;
    private Integer enabled;
    private LocalDateTime createTime;
}
