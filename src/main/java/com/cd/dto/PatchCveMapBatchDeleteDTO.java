package com.cd.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class PatchCveMapBatchDeleteDTO {

    @NotEmpty(message = "ids不能为空")
    private List<Long> ids;
}
