package com.cd.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AssetExportFileDTO {

    private String fileName;
    private byte[] content;
}
