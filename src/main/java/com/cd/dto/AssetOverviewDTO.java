package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资产总览：主机维度的资产统计。
 *
 * <p>每个字段对应一类资产的最近一次探测数量。updatedAt 取四类资产中最近一次更新的时间。</p>
 */
@Data
public class AssetOverviewDTO {

    private String hostName;
    private String macAddress;
    private Integer accountCount;
    private Integer serviceCount;
    private Integer processCount;
    private Integer appCount;
    private LocalDateTime updatedAt;
}
