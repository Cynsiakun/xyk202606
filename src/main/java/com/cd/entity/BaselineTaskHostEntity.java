package com.cd.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BaselineTaskHostEntity {

    private Long id;
    private Long taskId;
    private Long hostId;
    private String status;
    private String resultSummary;
    private LocalDateTime scanTime;
    private LocalDateTime createTime;
}
