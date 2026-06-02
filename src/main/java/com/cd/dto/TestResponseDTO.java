package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TestResponseDTO {

    private Integer id;
    private String name;
    private Integer status;
    private LocalDateTime createdAt;
}
