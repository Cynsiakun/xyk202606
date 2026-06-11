package com.cd.dto;

import lombok.Data;

/**
 * 安全事件顶部统计卡片：各等级数量与未处理数量。
 */
@Data
public class SecurityEventStatDTO {

    private long critical;
    private long high;
    private long medium;
    private long untreated;
}
