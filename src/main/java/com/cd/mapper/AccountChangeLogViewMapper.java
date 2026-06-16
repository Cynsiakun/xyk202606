package com.cd.mapper;

import com.cd.dto.AccountChangeLogDetailDTO;
import com.cd.dto.AccountChangeLogItemDTO;
import com.cd.dto.AccountChangeLogQueryDTO;
import com.cd.dto.HostOptionDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 账户变更日志只读查询，针对 {@code account_change_logs} 做服务端分页/筛选/排序。
 * 与 MQ 写入用的 {@link AccountChangeLogMapper} 隔离，互不影响。
 */
public interface AccountChangeLogViewMapper {

    List<AccountChangeLogItemDTO> selectPage(@Param("q") AccountChangeLogQueryDTO query);

    long countPage(@Param("q") AccountChangeLogQueryDTO query);

    AccountChangeLogDetailDTO selectDetailById(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /** 今日变更总数（按 event_time）。 */
    long countToday(@Param("tenantId") Long tenantId);

    /** 今日某动作数量（按 event_time）。 */
    long countTodayByAction(@Param("action") String action, @Param("tenantId") Long tenantId);

    List<HostOptionDTO> selectHostOptions(@Param("tenantId") Long tenantId);

    List<AccountChangeLogItemDTO> selectForExport(@Param("q") AccountChangeLogQueryDTO query, @Param("limit") int limit);
}
