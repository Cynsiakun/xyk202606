package com.cd.mapper;

import com.cd.entity.HostVulnTaskEntity;
import org.apache.ibatis.annotations.Param;

public interface HostVulnTaskMapper {

    int insert(HostVulnTaskEntity entity);

    HostVulnTaskEntity selectById(@Param("id") Long id);

    HostVulnTaskEntity selectByIdAndTenant(@Param("id") Long id,
                                           @Param("tenantId") Long tenantId);

    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("summaryJson") String summaryJson);

    int updateStatusByTenant(@Param("id") Long id,
                             @Param("status") Integer status,
                             @Param("summaryJson") String summaryJson,
                             @Param("tenantId") Long tenantId);

    int markFinished(@Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("summaryJson") String summaryJson);

    int markFinishedByTenant(@Param("id") Long id,
                             @Param("status") Integer status,
                             @Param("summaryJson") String summaryJson,
                             @Param("tenantId") Long tenantId);
}
