package com.cd.mapper;

import com.cd.dto.BaselineHostOverviewDTO;
import com.cd.dto.BaselineHostResultItemDTO;
import com.cd.dto.BaselineProblemHostDTO;
import com.cd.dto.BaselineRuleOptionDTO;
import com.cd.dto.BaselineTaskExportRowDTO;
import com.cd.dto.BaselineTaskListItemDTO;
import com.cd.dto.BaselineTaskResultOverviewDTO;
import com.cd.entity.BaselineAssetTypeEntity;
import com.cd.entity.BaselineProtectionLevelEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 「基线任务管理」与「主机合规总览」页面只读查询。
 */
public interface BaselineQueryMapper {

    List<BaselineTaskListItemDTO> selectTaskPage(@Param("keyword") String keyword,
                                                 @Param("executeType") String executeType,
                                                 @Param("taskType") String taskType,
                                                 @Param("status") String status,
                                                 @Param("tenantId") Long tenantId,
                                                 @Param("offset") int offset,
                                                 @Param("limit") int limit);

    long countTasks(@Param("keyword") String keyword,
                    @Param("executeType") String executeType,
                    @Param("taskType") String taskType,
                    @Param("status") String status,
                    @Param("tenantId") Long tenantId);

    BaselineTaskResultOverviewDTO selectResultOverview(@Param("taskId") Long taskId,
                                                       @Param("tenantId") Long tenantId);

    List<BaselineProblemHostDTO> selectProblemHostPage(@Param("taskId") Long taskId,
                                                       @Param("tenantId") Long tenantId,
                                                       @Param("assetTypeCode") String assetTypeCode,
                                                       @Param("offset") int offset,
                                                       @Param("limit") int limit);

    long countProblemHosts(@Param("taskId") Long taskId,
                           @Param("tenantId") Long tenantId,
                           @Param("assetTypeCode") String assetTypeCode);

    List<BaselineHostResultItemDTO> selectTaskHostResults(@Param("taskId") Long taskId,
                                                          @Param("hostId") Long hostId,
                                                          @Param("tenantId") Long tenantId);

    List<BaselineTaskExportRowDTO> selectTaskExportRows(@Param("taskId") Long taskId,
                                                        @Param("tenantId") Long tenantId);

    List<BaselineTaskExportRowDTO> selectHostExportRows(@Param("hostId") Long hostId,
                                                        @Param("tenantId") Long tenantId);

    List<BaselineRuleOptionDTO> selectRuleOptions(@Param("keyword") String keyword,
                                                  @Param("assetTypeCodes") List<String> assetTypeCodes,
                                                  @Param("protectionLevelCode") String protectionLevelCode);

    List<BaselineProtectionLevelEntity> selectProtectionLevels();

    List<BaselineAssetTypeEntity> selectAssetTypes();

    /** 主机合规总览：每台主机取最近一次基线汇总，支持关键字与合规等级筛选。 */
    List<BaselineHostOverviewDTO> selectHostOverviewPage(@Param("keyword") String keyword,
                                                         @Param("level") String level,
                                                         @Param("tenantId") Long tenantId,
                                                         @Param("offset") int offset,
                                                         @Param("limit") int limit);

    long countHostOverview(@Param("keyword") String keyword,
                           @Param("level") String level,
                           @Param("tenantId") Long tenantId);

    /** 主机详情：取该主机最近一次任务的逐规则检测结果，onlyFail=true 时仅返回 FAIL/ERROR。 */
    List<BaselineHostResultItemDTO> selectHostResults(@Param("hostId") Long hostId,
                                                      @Param("onlyFail") boolean onlyFail,
                                                      @Param("tenantId") Long tenantId);
}
