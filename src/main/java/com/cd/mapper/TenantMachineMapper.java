package com.cd.mapper;

import com.cd.entity.TenantMachineEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface TenantMachineMapper {

    int insert(TenantMachineEntity entity);

    int updateByIdAndTenant(TenantMachineEntity entity);

    int deleteByIdAndTenant(@Param("id") Long id, @Param("tenantId") Long tenantId);

    TenantMachineEntity selectByIdAndTenant(@Param("id") Long id, @Param("tenantId") Long tenantId);

    TenantMachineEntity selectByMachineId(@Param("machineId") String machineId);

    TenantMachineEntity selectByMacAddress(@Param("macAddress") String macAddress);

    TenantMachineEntity selectActiveByMachineOrMac(@Param("machineId") String machineId,
                                                   @Param("macAddress") String macAddress);

    TenantMachineEntity selectActiveByMacAddress(@Param("macAddress") String macAddress);

    int bindMachineIdIfEmpty(@Param("id") Long id, @Param("machineId") String machineId);

    List<TenantMachineEntity> selectPageByTenant(@Param("offset") int offset,
                                                 @Param("size") int size,
                                                 @Param("keyword") String keyword,
                                                 @Param("tenantId") Long tenantId);

    long countAllByTenant(@Param("keyword") String keyword, @Param("tenantId") Long tenantId);

    long countEnabledByTenant(@Param("tenantId") Long tenantId);
}
