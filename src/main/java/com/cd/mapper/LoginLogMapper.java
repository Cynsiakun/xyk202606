package com.cd.mapper;

import com.cd.entity.LoginLogEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LoginLogMapper {

    int insert(LoginLogEntity entity);

    List<LoginLogEntity> selectPage(@Param("offset") int offset,
                                    @Param("size") int size,
                                    @Param("userName") String userName,
                                    @Param("status") Integer status);

    long countAll(@Param("userName") String userName, @Param("status") Integer status);

    long countTodaySuccess();

    long countWeekActiveUsers();

    long countTotalLogs();
}
