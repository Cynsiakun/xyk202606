package com.cd.mapper;

import com.cd.dto.BaselineHostOverviewDTO;
import com.cd.dto.BaselineHostResultItemDTO;
import com.cd.dto.BaselineProblemHostDTO;
import com.cd.dto.BaselineRuleOptionDTO;
import com.cd.dto.BaselineTaskListItemDTO;
import com.cd.dto.BaselineTaskResultOverviewDTO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 「基线任务管理」与「主机合规总览」页面只读查询。
 */
public interface BaselineQueryMapper {

    List<BaselineTaskListItemDTO> selectTaskPage(@Param("status") String status,
                                                 @Param("offset") int offset,
                                                 @Param("limit") int limit);

    long countTasks(@Param("status") String status);

    BaselineTaskResultOverviewDTO selectResultOverview(@Param("taskId") Long taskId);

    List<BaselineProblemHostDTO> selectProblemHostPage(@Param("taskId") Long taskId,
                                                       @Param("offset") int offset,
                                                       @Param("limit") int limit);

    long countProblemHosts(@Param("taskId") Long taskId);

    List<BaselineRuleOptionDTO> selectRuleOptions(@Param("keyword") String keyword);

    /** 主机合规总览：每台主机取最近一次基线汇总，支持关键字与合规等级筛选。 */
    List<BaselineHostOverviewDTO> selectHostOverviewPage(@Param("keyword") String keyword,
                                                         @Param("level") String level,
                                                         @Param("offset") int offset,
                                                         @Param("limit") int limit);

    long countHostOverview(@Param("keyword") String keyword, @Param("level") String level);

    /** 主机详情：取该主机最近一次任务的逐规则检测结果，onlyFail=true 时仅返回 FAIL/ERROR。 */
    List<BaselineHostResultItemDTO> selectHostResults(@Param("hostId") Long hostId,
                                                      @Param("onlyFail") boolean onlyFail);
}
