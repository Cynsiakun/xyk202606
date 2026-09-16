package com.cd.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资产记录统一响应：用于四类资产的分页列表与详情。
 *
 * <p>assetJson 仅在详情接口返回；列表接口为节省带宽设为 null。</p>
 */
@Data
public class AssetRecordDTO {

    private Long id;
    private String taskId;
    private String hostName;
    private String macAddress;
    private String source;
    private String productName;
    private String productType;
    private Integer portCount;
    private Integer assetCount;
    private String assetJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
