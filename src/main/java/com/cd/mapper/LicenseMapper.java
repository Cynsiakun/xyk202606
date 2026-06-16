package com.cd.mapper;

import com.cd.entity.LicenseEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LicenseMapper {

    int insert(LicenseEntity entity);

    int updateById(LicenseEntity entity);

    int updateMachineAndSignature(@Param("id") Long id,
                                  @Param("machineId") String machineId,
                                  @Param("signature") String signature);

    int deleteById(@Param("id") Long id);

    LicenseEntity selectById(@Param("id") Long id);

    LicenseEntity selectByLicenseKey(@Param("licenseKey") String licenseKey);

    LicenseEntity selectEffectiveByTenantId(@Param("tenantId") Long tenantId);

    List<LicenseEntity> selectByTenantId(@Param("tenantId") Long tenantId);
}
