package com.cd.mapper;

import com.cd.entity.HostVulnTaskEntity;
import org.apache.ibatis.annotations.Param;

public interface HostVulnTaskMapper {

    int insert(HostVulnTaskEntity entity);

    HostVulnTaskEntity selectById(@Param("id") Long id);

    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("summaryJson") String summaryJson);

    int markFinished(@Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("summaryJson") String summaryJson);
}
