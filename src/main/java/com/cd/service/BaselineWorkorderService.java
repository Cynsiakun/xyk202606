package com.cd.service;

import com.cd.dto.BaselineActionResponseDTO;

import java.util.List;

/**
 * 基线整改工单：对手动/半自动修复的不合规结果创建工单。
 */
public interface BaselineWorkorderService {

    BaselineActionResponseDTO create(List<Long> resultIds, String assignee, String remark);
}
