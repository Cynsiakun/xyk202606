package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginLogResponseDTO {

    private Integer id;
    private Long userId;
    private String userName;
    private LocalDateTime loginTime;
    private String ipAddress;
    private Integer status;
    private String message;
}
