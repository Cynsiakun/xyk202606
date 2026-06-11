package com.cd.dto;

import lombok.Data;

/**
 * 账户变更日志顶部统计卡片：今日各动作数量（按 event_time 当天计）。
 */
@Data
public class AccountChangeLogStatDTO {

    private long todayTotal;
    private long todayCreate;
    private long todayModify;
    private long todayDelete;
}
