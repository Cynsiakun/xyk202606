package com.cd.mapper;

import com.cd.dto.PopupAlertDTO;
import com.cd.dto.SecurityEventDetailDTO;
import com.cd.dto.SecurityEventItemDTO;
import com.cd.dto.SecurityEventQueryDTO;
import com.cd.dto.HostOptionDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 安全事件（{@code security_alerts}）只读查询与状态流转。
 * 与 MQ 写入用的 {@link SecurityAlertMapper} 隔离，互不影响。
 */
public interface SecurityEventMapper {

    List<SecurityEventItemDTO> selectPage(@Param("q") SecurityEventQueryDTO query);

    long countPage(@Param("q") SecurityEventQueryDTO query);

    SecurityEventDetailDTO selectDetailById(@Param("id") Long id);

    long countByLevel(@Param("level") String level);

    long countByStatus(@Param("status") String status);

    /** 批量更新状态：仅当当前状态在 {@code fromStatuses} 内时才更新（空表示不限制）。 */
    int updateStatus(@Param("ids") List<Long> ids,
                     @Param("status") String status,
                     @Param("fromStatuses") List<String> fromStatuses);

    List<HostOptionDTO> selectHostOptions();

    List<SecurityEventItemDTO> selectForExport(@Param("q") SecurityEventQueryDTO query, @Param("limit") int limit);

    /** 当前最大告警 id（用于 WS 推送高水位初始化）。 */
    Long selectMaxId();

    /** 推送用：新出现的 Critical/High 且 status='new' 的告警（id 大于高水位）。 */
    List<PopupAlertDTO> selectNewHighCritical(@Param("afterId") Long afterId, @Param("limit") int limit);

    /** 连接初始同步：最近未处理的 Critical/High 告警。 */
    List<PopupAlertDTO> selectRecentHighCritical(@Param("limit") int limit);
}
