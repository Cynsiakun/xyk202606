package com.cd.dto;

import lombok.Data;

@Data
public class VulnVerificationTaskResponseDTO {

    private Long taskId;
    private Integer ruleCount;
    private Boolean sent;
    private Integer status;
    private String message;
}
