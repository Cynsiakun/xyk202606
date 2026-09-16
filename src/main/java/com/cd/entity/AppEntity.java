package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资产探测 — 软件数据。
 *
 * <p>存储从 {@code app_queue} 消费的探测结果，asset_json 为原始 apps 数组 JSON。
 * task_id 当前透传入库，后续可扩展为任务追踪标识。</p>
 */
@Data
public class AppEntity {

    private Long id;
    private Long tenantId;
    private String taskId;
    private String hostName;
    private String macAddress;
    private String source;
    private Integer assetCount;
    private String assetJson;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
