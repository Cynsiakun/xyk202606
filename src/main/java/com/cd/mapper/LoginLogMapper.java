package com.cd.mapper;

import com.cd.entity.LoginLogEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LoginLogMapper {

    int insert(LoginLogEntity entity);

    List<LoginLogEntity> selectPage(@Param("offset") int offset,
                                    @Param("size") int size,
                                    @Param("userName") String userName,
                                    @Param("status") Integer status,
                                    @Param("tenantId") Long tenantId);

    long countAll(@Param("userName") String userName,
                  @Param("status") Integer status,
                  @Param("tenantId") Long tenantId);

    long countTodaySuccess(@Param("tenantId") Long tenantId);

    long countWeekActiveUsers(@Param("tenantId") Long tenantId);

    long countTotalLogs(@Param("tenantId") Long tenantId);
}
