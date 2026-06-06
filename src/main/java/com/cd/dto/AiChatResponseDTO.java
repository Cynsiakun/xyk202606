package com.cd.dto;

import lombok.Data;

@Data
public class AiChatResponseDTO {

    private String model;
    private String promptName;
    private String content;
}
