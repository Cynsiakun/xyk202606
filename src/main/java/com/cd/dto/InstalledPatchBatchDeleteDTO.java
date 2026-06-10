package com.cd.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class InstalledPatchBatchDeleteDTO {

    @NotEmpty(message = "ids 不能为空")
    private List<Long> ids;
}
