package com.cd.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 全局自动探测策略的查询 / 保存载体。
 *
 * <p>前端以开关 + 周期下拉 + 探测内容复选框提交；后端以单行全局配置存储，调度器读取后据此下发。</p>
 */
@Data
public class ProbeStrategyDTO {

    /** 是否启用自动探测。 */
    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;

    /** 探测周期（小时）：仅允许 1 / 4 / 8 / 12 / 24。 */
    @NotNull(message = "periodHours 不能为空")
    private Integer periodHours;

    /** 探测内容：账号。 */
    private boolean account;

    /** 探测内容：服务。 */
    private boolean service;

    /** 探测内容：进程。 */
    private boolean process;

    /** 探测内容：应用。 */
    private boolean app;

    /** 上次自动探测下发时间（只读，前端展示用）。 */
    private LocalDateTime lastRunAt;
}
