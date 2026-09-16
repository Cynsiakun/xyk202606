package com.cd.service.impl;

import com.cd.common.ai.AiException;
import com.cd.common.ai.AiProperties;
import com.cd.common.license.LicenseFeature;
import com.cd.common.license.LicenseGuard;
import com.cd.dto.AiChatResponseDTO;
import com.cd.service.AiService;
import lombok.Data;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashScopeAiServiceImpl implements AiService {

    private static final String PROMPT_NAME_PATTERN = "^[A-Za-z0-9_-]+$";

    private final AiProperties aiProperties;
    private final ResourceLoader resourceLoader;
    private final LicenseGuard licenseGuard;
    private final RestClient restClient;

    public DashScopeAiServiceImpl(AiProperties aiProperties, ResourceLoader resourceLoader, LicenseGuard licenseGuard) {
        this.aiProperties = aiProperties;
        this.resourceLoader = resourceLoader;
        this.licenseGuard = licenseGuard;
        this.restClient = RestClient.builder()
                .baseUrl(removeTrailingSlash(aiProperties.getBaseUrl()))
                .build();
    }

    @Override
    public AiChatResponseDTO chat(String promptName, Map<String, Object> variables) {
        licenseGuard.requireFeature(LicenseFeature.AI);
        validateConfig();
        String prompt = renderPrompt(promptName, variables == null ? Map.of() : variables);
        return sendMessages(promptName, List.of(Map.of(
                "role", "user",
                "content", prompt
        )), false, null);
    }

    @Override
    public AiChatResponseDTO chatWithSystem(String systemPromptName, String userMessage) {
        licenseGuard.requireFeature(LicenseFeature.AI);
        validateConfig();
        String systemPrompt = loadPrompt(systemPromptName);
        return sendMessages(systemPromptName, List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage == null ? "" : userMessage)
        ), false, null);
    }

    @Override
    public AiChatResponseDTO chatJsonWithSystem(String systemPromptName, String userMessage, Integer maxTokens) {
        licenseGuard.requireFeature(LicenseFeature.AI);
        validateConfig();
        String systemPrompt = loadPrompt(systemPromptName);
        return sendMessages(systemPromptName, List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userMessage == null ? "" : userMessage)
        ), true, maxTokens);
    }

    private AiChatResponseDTO sendMessages(String promptName, List<Map<String, String>> messages,
                                           boolean jsonObject, Integer maxTokens) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", aiProperties.getModel());
        request.put("messages", messages);
        request.put("temperature", aiProperties.getTemperature());
        request.put("max_tokens", maxTokens == null ? aiProperties.getMaxTokens() : maxTokens);
        if (jsonObject) {
            request.put("response_format", Map.of("type", "json_object"));
        }

        DashScopeChatResponse response = restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (httpRequest, httpResponse) -> {
                    String body = StreamUtils.copyToString(httpResponse.getBody(), StandardCharsets.UTF_8);
                    throw new AiException("AI调用失败: HTTP " + httpResponse.getStatusCode().value() + " " + body);
                })
                .body(DashScopeChatResponse.class);

        String content = extractContent(response);
        AiChatResponseDTO dto = new AiChatResponseDTO();
        dto.setModel(aiProperties.getModel());
        dto.setPromptName(promptName);
        dto.setContent(content);
        return dto;
    }

    private String renderPrompt(String promptName, Map<String, Object> variables) {
        String template = loadPrompt(promptName);
        String prompt = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            prompt = prompt.replace("{{" + entry.getKey() + "}}", value);
        }
        return prompt;
    }

    private String loadPrompt(String promptName) {
        if (!StringUtils.hasText(promptName) || !promptName.matches(PROMPT_NAME_PATTERN)) {
            throw new IllegalArgumentException("promptName格式不正确");
        }
        Resource resource = resourceLoader.getResource("classpath:ai/" + promptName + ".txt");
        if (!resource.exists()) {
            throw new IllegalArgumentException("prompt不存在: " + promptName);
        }
        try {
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AiException("读取prompt失败: " + promptName, e);
        }
    }

    private void validateConfig() {
        if (!StringUtils.hasText(aiProperties.getApiKey())) {
            throw new AiException("请先设置环境变量DASHSCOPE_API_KEY");
        }
        if (!StringUtils.hasText(aiProperties.getBaseUrl())) {
            throw new AiException("AI服务地址不能为空");
        }
        if (!StringUtils.hasText(aiProperties.getModel())) {
            throw new AiException("AI模型不能为空");
        }
    }

    private String extractContent(DashScopeChatResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            throw new AiException("AI响应为空");
        }
        DashScopeMessage message = response.getChoices().get(0).getMessage();
        if (message == null || !StringUtils.hasText(message.getContent())) {
            throw new AiException("AI响应内容为空");
        }
        return message.getContent();
    }

    private static String removeTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    @Data
    private static class DashScopeChatResponse {
        private List<DashScopeChoice> choices;
    }

    @Data
    private static class DashScopeChoice {
        private DashScopeMessage message;
    }

    @Data
    private static class DashScopeMessage {
        private String content;
    }
}
