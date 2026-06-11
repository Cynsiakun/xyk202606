package com.cd.dto;

import lombok.Data;

import java.util.List;

@Data
public class BaselineTaskDispatchResponseDTO {

    private Long taskId;
    private String status;
    private Integer totalHostCount;
    private Integer sentCount;
    private Integer failedCount;
    private String message;
    private List<BaselineHostDispatchDTO> hosts;
}
