package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TenantResponseDTO {

    private Long id;
    private String name;
    private String contact;
    private Integer status;
    private LocalDateTime createdAt;
}
