package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserResponseDTO {

    private Long id;
    private String userName;
    private String userAvatar;
    private String userPhone;
    private String userEmail;
    private Integer status;
    private List<String> roles;
    private LocalDateTime createAt;
    private LocalDateTime updateAt;
    private LocalDateTime lastLoginTime;
}
