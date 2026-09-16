package com.cd.service;

public interface PortScanResultService {

    void processPortScanMessage(String queueName, String message);
}
