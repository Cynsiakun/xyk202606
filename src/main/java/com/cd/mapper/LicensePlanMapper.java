package com.cd.mapper;

import com.cd.entity.LicensePlanEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LicensePlanMapper {

    LicensePlanEntity selectByCode(@Param("code") String code);

    List<LicensePlanEntity> selectEnabled();
}
