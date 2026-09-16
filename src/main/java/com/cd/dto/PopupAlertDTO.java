package com.cd.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 全局告警弹窗推送体（仅 Critical / High）。通过 WebSocket 下发到前端外壳。
 */
@Data
public class PopupAlertDTO {

    private Long id;
    @JsonIgnore
    private Long tenantId;
    private String level;
    private Long hostId;
    private String hostname;
    private String alertName;
    private String description;
    private LocalDateTime eventTime;
}
