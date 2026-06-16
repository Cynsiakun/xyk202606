package com.cd.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PortScanDTO {

    @NotBlank(message = "MAC地址不能为空")
    private String macAddress;

    private String scanRange;
    private String customPorts;
    private boolean grabBanner;
    private boolean force;
}
