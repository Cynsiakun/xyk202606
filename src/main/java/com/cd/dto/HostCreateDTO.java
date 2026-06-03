package com.cd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HostCreateDTO {

    @Size(max = 255, message = "hostname 长度不能超过255")
    private String hostname;

    @Size(max = 64, message = "ipv4 长度不能超过64")
    private String ipv4;

    @NotBlank(message = "MAC地址不能为空")
    @Size(max = 64, message = "macAddress 长度不能超过64")
    private String macAddress;

    @Size(max = 100, message = "osName 长度不能超过100")
    private String osName;

    @Size(max = 100, message = "osVersion 长度不能超过100")
    private String osVersion;

    @Size(max = 50, message = "osArch 长度不能超过50")
    private String osArch;

    @Size(max = 100, message = "osRelease 长度不能超过100")
    private String osRelease;

    @Size(max = 255, message = "cpuModel 长度不能超过255")
    private String cpuModel;

    private Integer cpuPhysicalCores;

    private Integer cpuLogicalCores;

    @Size(max = 50, message = "memTotal 长度不能超过50")
    private String memTotal;

    @Size(max = 50, message = "memUsed 长度不能超过50")
    private String memUsed;

    @Size(max = 50, message = "memAvailable 长度不能超过50")
    private String memAvailable;

    @Size(max = 50, message = "memUsage 长度不能超过50")
    private String memUsage;

    private Integer status;
}
