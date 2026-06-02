package com.cd.mapper;

import com.cd.entity.UserEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper {

    int insert(UserEntity entity);

    int deleteById(@Param("id") Long id);

    int updateById(UserEntity entity);

    int updateSelfById(UserEntity entity);

    int updatePasswordById(@Param("id") Long id, @Param("userPwd") String userPwd);

    UserEntity selectById(@Param("id") Long id);

    List<UserEntity> selectPage(@Param("offset") int offset,
                                @Param("size") int size,
                                @Param("userName") String userName);

    long countAll(@Param("userName") String userName);

    UserEntity selectByUserName(@Param("userName") String userName);

    UserEntity selectByUserPhone(@Param("userPhone") String userPhone);

    UserEntity selectByUserEmail(@Param("userEmail") String userEmail);

    int updateLastLoginTime(@Param("id") Long id);

    long countCreatedToday();
}
