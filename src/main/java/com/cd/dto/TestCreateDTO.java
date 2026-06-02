package com.cd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TestCreateDTO {

    @NotBlank(message = "name不能为空")
    @Size(min = 1, max = 50, message = "name长度必须在1到50之间")
    private String name;

    private Integer status;
}
