package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TestEntity {

    private Integer id;
    private String name;
    private Integer status;
    private LocalDateTime createdAt;
}
