package com.cd.mapper;

import com.cd.entity.HostVulnResultEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface HostVulnResultMapper {

    int insert(HostVulnResultEntity entity);

    int markInactiveByHostId(@Param("hostId") Long hostId);

    List<HostVulnResultEntity> selectActiveByHostId(@Param("hostId") Long hostId);
}
