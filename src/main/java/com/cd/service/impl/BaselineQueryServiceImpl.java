package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.dto.BaselineProblemHostDTO;
import com.cd.dto.BaselineRuleOptionDTO;
import com.cd.dto.BaselineTaskListItemDTO;
import com.cd.dto.BaselineTaskResultOverviewDTO;
import com.cd.mapper.BaselineQueryMapper;
import com.cd.service.BaselineQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BaselineQueryServiceImpl implements BaselineQueryService {

    private final BaselineQueryMapper baselineQueryMapper;

    @Override
    public PageResult<BaselineTaskListItemDTO> listTasks(Integer page, Integer size, String status) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        String safeStatus = normalizeStatus(status);
        long total = baselineQueryMapper.countTasks(safeStatus);
        List<BaselineTaskListItemDTO> list = baselineQueryMapper.selectTaskPage(
                safeStatus, (safePage - 1) * safeSize, safeSize);
        return new PageResult<>(total, list);
    }

    @Override
    public BaselineTaskResultOverviewDTO getResultOverview(Long taskId) {
        BaselineTaskResultOverviewDTO overview = baselineQueryMapper.selectResultOverview(taskId);
        if (overview == null) {
            overview = new BaselineTaskResultOverviewDTO();
            overview.setTaskId(taskId);
            overview.setTotalHostCount(0);
            overview.setFinishedHostCount(0);
            overview.setPassHostCount(0);
            overview.setFailHostCount(0);
        }
        return overview;
    }

    @Override
    public PageResult<BaselineProblemHostDTO> listProblemHosts(Long taskId, Integer page, Integer size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        long total = baselineQueryMapper.countProblemHosts(taskId);
        List<BaselineProblemHostDTO> list = baselineQueryMapper.selectProblemHostPage(
                taskId, (safePage - 1) * safeSize, safeSize);
        return new PageResult<>(total, list);
    }

    @Override
    public List<BaselineRuleOptionDTO> listRuleOptions(String keyword) {
        String trimmed = keyword == null ? null : keyword.trim();
        return baselineQueryMapper.selectRuleOptions(trimmed);
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return 10;
        }
        return Math.min(size, 200);
    }

    private String normalizeStatus(String status) {
        return status == null || status.trim().isEmpty() ? null : status.trim();
    }
}
