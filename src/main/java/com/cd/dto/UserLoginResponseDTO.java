package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserLoginResponseDTO {

    private String token;
    private Long userId;
    private Long tenantId;
    private String userName;
    private LocalDateTime lastLoginTime;
}
