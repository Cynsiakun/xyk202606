package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AssetExportLogEntity {

    private Long id;
    private Long userId;
    private Long hostId;
    private LocalDateTime exportTime;
    private String exportFormat;
    private String ipAddress;
}
