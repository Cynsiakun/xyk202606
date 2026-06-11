package com.cd.service;

import com.cd.entity.BaselineCheckDataEntity;

/**
 * 基线检测规则引擎：将 {@code baseline_check_data} 中的原始数据逐 item 与规则参数比对，
 * 判定 PASS/FAIL/ERROR 并写入 {@code baseline_result}，同时更新任务与主机执行状态。
 *
 * <p>本阶段只做规则判定与结果入库，不涉及修复与工单。处理保证幂等：同一 task_host
 * 已有结果时直接跳过，不重复生成 result。</p>
 */
public interface BaselineRuleEngine {

    /**
     * 处理一条原始检测数据。
     *
     * @param checkData baseline_check_data 记录，{@code checkData} 字段为客户端回传的完整 JSON
     */
    void evaluate(BaselineCheckDataEntity checkData);
}
