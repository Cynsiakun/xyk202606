package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.BaselineActionResponseDTO;
import com.cd.dto.BaselineOperatorOptionDTO;
import com.cd.dto.BaselineWorkorderDetailDTO;
import com.cd.dto.BaselineWorkorderListItemDTO;

import java.util.List;

/**
 * 基线整改工单：对手动/半自动修复的不合规结果创建工单。
 */
public interface BaselineWorkorderService {

    BaselineActionResponseDTO create(List<Long> resultIds, Long assigneeId, String remark);

    PageResult<BaselineWorkorderListItemDTO> list(Integer page, Integer size, String keyword, String status, String priority);

    BaselineWorkorderDetailDTO detail(Long id);

    BaselineActionResponseDTO start(Long id);

    BaselineActionResponseDTO complete(Long id, String closeRemark);

    BaselineActionResponseDTO recheck(Long id);

    List<BaselineOperatorOptionDTO> operatorOptions();
}
