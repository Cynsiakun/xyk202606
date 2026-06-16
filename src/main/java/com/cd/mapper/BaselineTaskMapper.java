package com.cd.mapper;

import com.cd.entity.BaselineTaskEntity;
import com.cd.entity.BaselineTaskHostEntity;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface BaselineTaskMapper {

    int insertTask(BaselineTaskEntity entity);

    int insertTaskHost(BaselineTaskHostEntity entity);

    int updateTaskStatus(@Param("id") Long id,
                         @Param("status") String status,
                         @Param("successCount") Integer successCount,
                         @Param("failCount") Integer failCount);

    int updateTaskStatusByTenant(@Param("id") Long id,
                                 @Param("status") String status,
                                 @Param("successCount") Integer successCount,
                                 @Param("failCount") Integer failCount,
                                 @Param("tenantId") Long tenantId);

    int updateTaskHostStatus(@Param("id") Long id,
                             @Param("status") String status,
                             @Param("resultSummary") String resultSummary);

    int updateTaskHostStatusByTenant(@Param("id") Long id,
                                     @Param("status") String status,
                                     @Param("resultSummary") String resultSummary,
                                     @Param("tenantId") Long tenantId);

    /** 查询任务主记录。 */
    BaselineTaskEntity selectTaskById(@Param("id") Long id);

    BaselineTaskEntity selectTaskByIdAndTenant(@Param("id") Long id,
                                               @Param("tenantId") Long tenantId);

    /** 查询某任务下某主机的关联记录（规则引擎需 task_host_id 落库与幂等判断）。 */
    BaselineTaskHostEntity selectTaskHostByTaskAndHost(@Param("taskId") Long taskId,
                                                       @Param("hostId") Long hostId);

    /** 将 task_host 标记为终态并写入汇总、扫描时间。 */
    int finishTaskHost(@Param("id") Long id,
                       @Param("status") String status,
                       @Param("resultSummary") String resultSummary,
                       @Param("scanTime") LocalDateTime scanTime);

    /** 统计某任务下尚未进入终态（FINISHED/FAILED）的主机数量。 */
    int countUnfinishedHosts(@Param("taskId") Long taskId);

    /** 统计某任务下处于指定状态的主机数量。 */
    int countHostsByStatus(@Param("taskId") Long taskId, @Param("status") String status);

    /** 查询某主机最近一次任务的 rule_scope（JSON），用于「立即检测」沿用历史规则。 */
    String selectLatestRuleScopeByHost(@Param("hostId") Long hostId);

    String selectLatestRuleScopeByHostAndTenant(@Param("hostId") Long hostId,
                                                @Param("tenantId") Long tenantId);
}
