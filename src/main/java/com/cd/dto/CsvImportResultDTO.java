package com.cd.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CsvImportResultDTO {

    private int successCount;
    private int insertedCount;
    private int updatedCount;
    private int failureCount;
    private List<String> errorMessages = new ArrayList<>();

    public void incrementInserted() {
        this.insertedCount++;
        this.successCount++;
    }

    public void incrementUpdated() {
        this.updatedCount++;
        this.successCount++;
    }

    public void addFailure(String message) {
        this.failureCount++;
        this.errorMessages.add(message);
    }

    public void merge(CsvImportResultDTO other) {
        if (other == null) {
            return;
        }
        this.successCount += other.successCount;
        this.insertedCount += other.insertedCount;
        this.updatedCount += other.updatedCount;
        this.failureCount += other.failureCount;
        this.errorMessages.addAll(other.errorMessages);
    }
}
