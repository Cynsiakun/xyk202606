package com.cd.service;

public interface PatchScanService {

    void processPatchScanMessage(String queueName, String message);
}
