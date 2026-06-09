package com.cd.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetInfoDTO {

    private Long hostId;
    private String type;
    private String name;
    private String version;
    private String command;
    private String source;
}
