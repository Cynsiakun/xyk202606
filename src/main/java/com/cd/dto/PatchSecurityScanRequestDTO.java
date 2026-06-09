package com.cd.dto;

import lombok.Data;

import java.util.List;

@Data
public class PatchSecurityScanRequestDTO {

    private List<Long> hostIds;
}
