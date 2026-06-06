package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HostEntity {

    private Long id;
    private String hostname;
    private String ipv4;
    private String macAddress;
    private String osName;
    private String osVersion;
    private String osArch;
    private String osRelease;
    private String cpuModel;
    private Integer cpuPhysicalCores;
    private Integer cpuLogicalCores;
    private String memTotal;
    private String memUsed;
    private String memAvailable;
    private String memUsage;
    private Integer status;
    private LocalDateTime lastScanTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
