package com.cd.service;

import com.cd.dto.AiChatResponseDTO;

import java.util.Map;

public interface AiService {

    AiChatResponseDTO chat(String promptName, Map<String, Object> variables);

    AiChatResponseDTO chatWithSystem(String systemPromptName, String userMessage);

    AiChatResponseDTO chatJsonWithSystem(String systemPromptName, String userMessage, Integer maxTokens);
}
