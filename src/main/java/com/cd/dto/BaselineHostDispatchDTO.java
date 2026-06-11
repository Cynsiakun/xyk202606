package com.cd.dto;

import lombok.Data;

@Data
public class BaselineHostDispatchDTO {

    private Long hostId;
    private Long taskHostId;
    private String macAddress;
    private Boolean sent;
    private String message;
}
