package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserEntity {

    private Long id;
    private Long tenantId;
    private String userName;
    private String userPwd;
    private String userAvatar;
    private String userPhone;
    private String userEmail;
    private Integer status;
    private LocalDateTime createAt;
    private LocalDateTime updateAt;
    private LocalDateTime lastLoginTime;
}
