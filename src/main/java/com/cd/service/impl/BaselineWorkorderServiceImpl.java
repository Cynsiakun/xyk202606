package com.cd.service.impl;

import com.cd.common.security.SecurityUtils;
import com.cd.dto.BaselineActionResponseDTO;
import com.cd.entity.BaselineResultEntity;
import com.cd.entity.BaselineWorkorderEntity;
import com.cd.mapper.BaselineResultMapper;
import com.cd.mapper.BaselineWorkorderMapper;
import com.cd.service.BaselineWorkorderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaselineWorkorderServiceImpl implements BaselineWorkorderService {

    private static final String STATUS_OPEN = "OPEN";
    private static final String REMEDIATION_TICKETED = "TICKETED";

    private final BaselineResultMapper baselineResultMapper;
    private final BaselineWorkorderMapper baselineWorkorderMapper;

    @Override
    @Transactional
    public BaselineActionResponseDTO create(List<Long> resultIds, String assignee, String remark) {
        List<Long> distinctIds = distinctPositiveIds(resultIds);
        if (distinctIds.isEmpty()) {
            throw new IllegalArgumentException("工单目标不能为空");
        }
        List<BaselineResultEntity> results = baselineResultMapper.selectByIds(distinctIds);
        String creator = currentUsername();
        String safeAssignee = StringUtils.hasText(assignee) ? assignee.trim() : null;
        String safeRemark = StringUtils.hasText(remark) ? remark.trim() : null;

        List<Long> ticketedIds = new ArrayList<>();
        for (BaselineResultEntity result : results) {
            BaselineWorkorderEntity workorder = new BaselineWorkorderEntity();
            workorder.setHostId(result.getHostId());
            workorder.setRuleId(result.getRuleId());
            workorder.setResultId(result.getId());
            workorder.setAssignee(safeAssignee);
            workorder.setRemark(safeRemark);
            workorder.setStatus(STATUS_OPEN);
            workorder.setCreator(creator);
            baselineWorkorderMapper.insert(workorder);
            ticketedIds.add(result.getId());
        }
        if (!ticketedIds.isEmpty()) {
            baselineResultMapper.updateRemediationStatusByIds(ticketedIds, REMEDIATION_TICKETED);
        }

        int success = ticketedIds.size();
        int failed = distinctIds.size() - success;
        BaselineActionResponseDTO response = new BaselineActionResponseDTO();
        response.setTotal(distinctIds.size());
        response.setSuccess(success);
        response.setFailed(failed);
        response.setMessage(failed == 0
                ? "已创建 " + success + " 个工单"
                : "成功 " + success + " 个，失败 " + failed + " 个");
        return response;
    }

    private List<Long> distinctPositiveIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return new ArrayList<>(new LinkedHashSet<>(ids.stream()
                .filter(id -> id != null && id > 0)
                .toList()));
    }

    private String currentUsername() {
        String username = SecurityUtils.getCurrentUsername();
        return StringUtils.hasText(username) ? username : "system";
    }
}
