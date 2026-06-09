package com.cd.dto;

import lombok.Data;

import java.util.List;

@Data
public class PatchSecurityAnalyzeRequestDTO {

    private List<Long> hostIds;
}
