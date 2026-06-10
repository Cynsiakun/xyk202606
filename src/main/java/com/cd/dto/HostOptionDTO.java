package com.cd.dto;

import lombok.Data;

/**
 * 高级搜索的主机下拉选项。
 */
@Data
public class HostOptionDTO {

    private Long id;
    private String hostname;
    private String ipv4;
}
