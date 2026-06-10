package com.cd.mapper;

import com.cd.entity.WindowsEventLogEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code windows_event_logs} 持久化。
 */
public interface WindowsEventLogMapper {

    /**
     * 批量插入，使用 {@code INSERT IGNORE} 实现幂等：命中唯一键 {@code uk_host_log_rec}
     * 的重复记录被静默忽略，不会报错也不会产生重复行。
     *
     * @return 实际写入的行数（受影响行数）。重复条数 = 提交条数 - 返回值。
     */
    int insertBatchIgnore(@Param("list") List<WindowsEventLogEntity> list);

    /**
     * 按幂等键 {@code (host_id, log_type, record_number)} 反查这些行的 {@code id}，
     * 用于分流写入子表时确定 {@code source_log_id}。无论是本批新插入还是历史已存在（重复消费），
     * 都能取回该行当前的稳定 id。
     *
     * @param list 仅使用每个元素的 hostId / logType / recordNumber 三个字段
     * @return 命中的行，仅 id / hostId / logType / recordNumber 字段有值
     */
    List<WindowsEventLogEntity> selectIdsByKeys(@Param("list") List<WindowsEventLogEntity> list);
}
