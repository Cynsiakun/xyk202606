package com.cd.service;

public interface PortFingerprintService {

    void processExistingResults();

    void processPortScanResult(Long resultId);

    int rematchByTask(Long inventoryId, Long tenantId);

    int rematchLatestByMac(String macAddress, Long tenantId);
}
