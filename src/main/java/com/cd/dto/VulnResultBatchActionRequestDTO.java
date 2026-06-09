package com.cd.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class VulnResultBatchActionRequestDTO {

    @NotEmpty(message = "resultIds不能为空")
    private List<Long> resultIds;
}
