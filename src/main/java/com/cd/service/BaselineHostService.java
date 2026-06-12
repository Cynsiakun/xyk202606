package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.BaselineActionResponseDTO;
import com.cd.dto.BaselineHostOverviewDTO;
import com.cd.dto.BaselineHostResultItemDTO;

import java.util.List;

/**
 * 主机视角的合规总览：卡片列表、主机详情、单台/批量立即检测。
 */
public interface BaselineHostService {

    PageResult<BaselineHostOverviewDTO> listHostOverview(Integer page, Integer size, String keyword, String level);

    List<BaselineHostResultItemDTO> listHostResults(Long hostId, boolean onlyFail);

    /** 对所选主机各自下发一次基线扫描，规则沿用该主机最近一次任务，无历史时回退全部已发布规则。 */
    BaselineActionResponseDTO scanHosts(List<Long> hostIds);

    /** 对所选结果涉及的规则下发小范围复检任务。 */
    BaselineActionResponseDTO recheckResults(List<Long> resultIds);
}
