package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.BaselineProblemHostDTO;
import com.cd.dto.BaselineHostResultItemDTO;
import com.cd.dto.BaselineRuleOptionDTO;
import com.cd.dto.BaselineTaskListItemDTO;
import com.cd.dto.BaselineTaskResultOverviewDTO;
import com.cd.entity.BaselineAssetTypeEntity;
import com.cd.entity.BaselineProtectionLevelEntity;

import java.util.List;

/**
 * 「基线任务管理」页面只读查询服务。
 */
public interface BaselineQueryService {

    PageResult<BaselineTaskListItemDTO> listTasks(Integer page, Integer size, String keyword,
                                                  String executeType, String taskType, String status);

    BaselineTaskResultOverviewDTO getResultOverview(Long taskId);

    PageResult<BaselineProblemHostDTO> listProblemHosts(Long taskId, Integer page, Integer size, String assetTypeCode);

    List<BaselineHostResultItemDTO> listTaskHostResults(Long taskId, Long hostId);

    List<BaselineRuleOptionDTO> listRuleOptions(String keyword, List<String> assetTypeCodes, String protectionLevelCode);

    List<BaselineProtectionLevelEntity> listProtectionLevels();

    List<BaselineAssetTypeEntity> listAssetTypes();

    byte[] exportTaskResultCsv(Long taskId);

    byte[] exportTaskResultHtml(Long taskId);

    byte[] exportTaskResultPdf(Long taskId);

    byte[] exportHostResultCsv(Long hostId);

    byte[] exportHostResultHtml(Long hostId);

    byte[] exportHostResultPdf(Long hostId);
}
