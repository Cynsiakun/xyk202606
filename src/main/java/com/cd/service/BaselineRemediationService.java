package com.cd.service;

import com.cd.dto.BaselineActionResponseDTO;

import java.util.List;

/**
 * 基线自动修复：对不合规结果下发修复脚本（MQ），并回填修复结果。
 */
public interface BaselineRemediationService {

    /** 对所选结果下发修复脚本，置 remediation_status=IN_PROGRESS。 */
    BaselineActionResponseDTO remediate(List<Long> resultIds);

    /** 对最近一次成功修复记录下发回滚指令，成功回滚后该结果可再次修复。 */
    BaselineActionResponseDTO rollback(List<Long> resultIds);

    /** 处理 agent 回传的修复结果，按 resultId 更新为 COMPLETED/FAILED。 */
    void handleResult(Long remediationId, Long resultId, boolean success,
                      String oldValue, String newValue, String backupData);

    /** 处理 agent 回传的回滚结果。 */
    void handleRollbackResult(Long remediationId, Long resultId, boolean success);
}
