package com.cd.mapper;

import com.cd.dto.HostOptionDTO;
import com.cd.dto.LoginSecurityLogDetailDTO;
import com.cd.dto.LoginSecurityLogItemDTO;
import com.cd.dto.LoginSecurityLogQueryDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 登录日志只读查询，针对 {@code login_security_logs} 做服务端分页/筛选/排序。
 * 与 MQ 写入用的 {@link LoginSecurityLogMapper} 隔离，互不影响。
 */
public interface LoginSecurityLogViewMapper {

    List<LoginSecurityLogItemDTO> selectPage(@Param("q") LoginSecurityLogQueryDTO query);

    long countPage(@Param("q") LoginSecurityLogQueryDTO query);

    LoginSecurityLogDetailDTO selectDetailById(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /** 今日某结果数量（按 event_time）。 */
    long countTodayByResult(@Param("result") String result, @Param("tenantId") Long tenantId);

    /** 今日提权登录数量（按 event_time）。 */
    long countTodayElevated(@Param("tenantId") Long tenantId);

    List<HostOptionDTO> selectHostOptions(@Param("tenantId") Long tenantId);

    List<LoginSecurityLogItemDTO> selectForExport(@Param("q") LoginSecurityLogQueryDTO query, @Param("limit") int limit);
}
