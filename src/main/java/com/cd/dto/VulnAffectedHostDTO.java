package com.cd.dto;

import lombok.Data;

@Data
public class VulnAffectedHostDTO {

    private Long resultId;
    private String resultIdsCsv;
    private Long hostId;
    private String hostname;
    private String ipv4;
    private String macAddress;
    private String osName;
    private String osVersion;
    private String verifyStatus;
    private Integer status;
    private String productName;
    private String productVersion;
    private Integer matchedItemCount;
    private String matchedItemSummary;
}
