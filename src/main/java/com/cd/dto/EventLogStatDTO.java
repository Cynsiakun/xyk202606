package com.cd.dto;

import lombok.Data;

/**
 * 日志中心顶部统计卡片：今日各类型 / 错误日志数量（按 event_time 当天计）。
 */
@Data
public class EventLogStatDTO {

    private long todaySecurity;
    private long todaySystem;
    private long todayApplication;
    private long todayError;
}
