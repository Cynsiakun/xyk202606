package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HostAssetInventoryEntity {

    private Long id;
    private Long tenantId;
    private Long hostId;
    private String macAddress;
    private String taskId;
    private String source;
    private String category;
    private String subCategory;
    private String vendor;
    private String productVersion;
    private String protocol;
    private Integer port;
    private String productName;
    private String productType;
    private Integer confidence;
    private Long ruleId;
    private String bannerRaw;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime firstSeen;
    private LocalDateTime lastSeen;
}
