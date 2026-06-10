package com.cd.mapper;

import com.cd.dto.EventLogDetailDTO;
import com.cd.dto.EventLogItemDTO;
import com.cd.dto.EventLogQueryDTO;
import com.cd.dto.HostOptionDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 日志中心只读查询，针对 {@code windows_event_logs}（百万级）做服务端分页/筛选/排序。
 * 与 MQ 写入用的 {@link WindowsEventLogMapper} 隔离，互不影响。
 */
public interface SecurityLogCenterMapper {

    /** 条件分页查询（不含 raw_xml）。 */
    List<EventLogItemDTO> selectPage(@Param("q") EventLogQueryDTO query);

    /** 条件总数，用于分页。 */
    long countPage(@Param("q") EventLogQueryDTO query);

    /** 单条详情（含 raw_xml）。 */
    EventLogDetailDTO selectDetailById(@Param("id") Long id);

    /** 今日某日志类型数量（按 event_time）。 */
    long countTodayByType(@Param("logType") String logType);

    /** 今日错误级别数量（Error / Critical，按 event_time）。 */
    long countTodayError();

    /** 主机下拉选项。 */
    List<HostOptionDTO> selectHostOptions();

    /** 导出用：与列表相同条件，最多取 {@code limit} 行。 */
    List<EventLogItemDTO> selectForExport(@Param("q") EventLogQueryDTO query, @Param("limit") int limit);
}
