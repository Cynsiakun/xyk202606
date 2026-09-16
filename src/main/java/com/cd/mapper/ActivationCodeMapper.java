package com.cd.mapper;

import com.cd.entity.ActivationCodeEntity;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ActivationCodeMapper {

    int insert(ActivationCodeEntity entity);

    ActivationCodeEntity selectById(@Param("id") Long id);

    ActivationCodeEntity selectByCode(@Param("code") String code);

    List<ActivationCodeEntity> selectByTenantId(@Param("tenantId") Long tenantId);

    int markUsed(@Param("id") Long id,
                 @Param("boundMachineId") String boundMachineId,
                 @Param("boundMacAddress") String boundMacAddress,
                 @Param("boundHostName") String boundHostName,
                 @Param("usedAt") LocalDateTime usedAt);
}
