package com.cd.dto;

import lombok.Data;

@Data
public class AccountRiskSnapshotDTO {

    private Long hostId;
    private Long accountId;
    private String macAddress;
    private String assetJson;
}
