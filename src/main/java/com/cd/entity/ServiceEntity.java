package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资产探测 — 服务数据。
 *
 * <p>存储从 {@code service_queue} 消费的探测结果，asset_json 为原始 services 数组 JSON。
 * task_id 当前透传入库，后续可扩展为任务追踪标识。</p>
 */
@Data
public class ServiceEntity {

    private Long id;
    private String taskId;
    private String hostName;
    private String macAddress;
    private Integer assetCount;
    private String assetJson;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
