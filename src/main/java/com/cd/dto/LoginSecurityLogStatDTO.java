package com.cd.dto;

import lombok.Data;

/**
 * 登录日志顶部统计卡片：今日各结果数量（按 event_time 当天计）。
 */
@Data
public class LoginSecurityLogStatDTO {

    private long todaySuccess;
    private long todayFail;
    private long todayLogout;
    private long todayElevated;
}
