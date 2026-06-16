package com.cd.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TenantMachineUpdateDTO {

    @Size(max = 128, message = "machineId length must be less than or equal to 128")
    private String machineId;

    @Size(max = 64, message = "macAddress length must be less than or equal to 64")
    private String macAddress;

    @Size(max = 255, message = "hostName length must be less than or equal to 255")
    private String hostName;

    @Size(max = 255, message = "remark length must be less than or equal to 255")
    private String remark;

    private Integer status;
}
