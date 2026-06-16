package com.cd.dto;

import lombok.Data;

import java.util.List;

@Data
public class LicensePlanResponseDTO {

    private Long id;
    private String code;
    private String name;
    private Integer userLimit;
    private Integer hostLimit;
    private List<String> featureFlags;
}
