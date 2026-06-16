package com.cd.mapper;

import com.cd.entity.UserEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper {

    int insert(UserEntity entity);

    int deleteById(@Param("id") Long id);

    int deleteByIdAndTenant(@Param("id") Long id, @Param("tenantId") Long tenantId);

    int updateById(UserEntity entity);

    int updateByIdAndTenant(UserEntity entity);

    int updateSelfById(UserEntity entity);

    int updatePasswordById(@Param("id") Long id, @Param("userPwd") String userPwd);

    UserEntity selectById(@Param("id") Long id);

    UserEntity selectByIdAndTenant(@Param("id") Long id, @Param("tenantId") Long tenantId);

    List<UserEntity> selectPage(@Param("offset") int offset,
                                @Param("size") int size,
                                @Param("userName") String userName);

    List<UserEntity> selectPageByTenant(@Param("offset") int offset,
                                        @Param("size") int size,
                                        @Param("userName") String userName,
                                        @Param("tenantId") Long tenantId);

    long countAll(@Param("userName") String userName);

    long countAllByTenant(@Param("userName") String userName, @Param("tenantId") Long tenantId);

    UserEntity selectByUserName(@Param("userName") String userName);

    UserEntity selectByUserNameAndTenant(@Param("userName") String userName, @Param("tenantId") Long tenantId);

    UserEntity selectByUserPhone(@Param("userPhone") String userPhone);

    UserEntity selectByUserPhoneAndTenant(@Param("userPhone") String userPhone, @Param("tenantId") Long tenantId);

    UserEntity selectByUserEmail(@Param("userEmail") String userEmail);

    UserEntity selectByUserEmailAndTenant(@Param("userEmail") String userEmail, @Param("tenantId") Long tenantId);

    int updateLastLoginTime(@Param("id") Long id);

    long countCreatedToday();

    long countCreatedTodayByTenant(@Param("tenantId") Long tenantId);
}
