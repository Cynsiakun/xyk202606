package com.cd.service.impl;

import com.cd.common.ai.AiException;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.dto.AccountRiskAnalysisDTO;
import com.cd.dto.AiChatResponseDTO;
import com.cd.dto.AssetRecordDTO;
import com.cd.entity.AccountEntity;
import com.cd.mapper.AccountMapper;
import com.cd.service.AiService;
import com.cd.service.AssetAiAnalysisService;
import com.cd.service.AssetQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AssetAiAnalysisServiceImpl implements AssetAiAnalysisService {

    private static final String ACCOUNT_PROMPT = "asset-account-analysis";
    private static final int ACCOUNT_ANALYSIS_MAX_TOKENS = 8192;
    private static final int ERROR_PREVIEW_LENGTH = 300;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AccountMapper accountMapper;
    private final AiService aiService;
    private final AssetQueryService assetQueryService;

    @Override
    public AssetRecordDTO analyzeAccount(Long id, String assetJson) {
        AccountEntity entity = accountMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("账号资产记录不存在 id=" + id);
        }

        ArrayNode accounts = readAccounts(StringUtils.hasText(assetJson) ? assetJson : entity.getAssetJson());
        ObjectNode userMessage = buildAccountUserMessage(entity, accounts);

        AiChatResponseDTO aiResponse = aiService.chatJsonWithSystem(
                ACCOUNT_PROMPT,
                userMessage.toString(),
                ACCOUNT_ANALYSIS_MAX_TOKENS
        );
        ObjectNode analyzedRoot = readAiJson(aiResponse.getContent());
        ArrayNode analyzedAccounts = extractAccounts(analyzedRoot);
        syncShadowAccounts(analyzedAccounts);

        entity.setAssetJson(analyzedAccounts.toString());
        entity.setAssetCount(analyzedAccounts.size());
        accountMapper.updateAssetJsonById(entity.getId(), entity.getAssetJson(), entity.getAssetCount());

        return assetQueryService.accountDetail(id);
    }

    private ArrayNode readAccounts(String assetJson) {
        if (!StringUtils.hasText(assetJson)) {
            throw new IllegalArgumentException("账号资产数据为空");
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(assetJson);
            if (!node.isArray()) {
                throw new AiException("AI分析失败：账号资产数据必须是数组");
            }
            return (ArrayNode) node;
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("账号资产JSON格式异常");
        }
    }

    private ObjectNode buildAccountUserMessage(AccountEntity entity, ArrayNode accounts) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("taskId", entity.getTaskId());
        root.put("type", "account");
        root.put("hostName", entity.getHostName());
        root.put("macAddress", entity.getMacAddress());
        root.set("accounts", accounts);
        root.put("account_count", accounts.size());
        return root;
    }

    private ObjectNode readAiJson(String content) {
        if (!StringUtils.hasText(content)) {
            throw new AiException("AI返回内容为空");
        }
        String json = extractJsonObject(content);
        if (!StringUtils.hasText(json)) {
            throw new AiException("AI返回格式异常：未找到JSON对象，返回片段：" + preview(content));
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            if (node.isTextual()) {
                node = OBJECT_MAPPER.readTree(node.asText());
            }
            if (!node.isObject()) {
                throw new AiException("AI返回格式异常：必须返回JSON对象");
            }
            return (ObjectNode) node;
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException("AI返回格式异常：不是合法JSON，返回片段：" + preview(content));
        }
    }

    private String extractJsonObject(String content) {
        String text = content.trim();
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            int fenceEnd = text.lastIndexOf("```");
            if (firstLineEnd >= 0 && fenceEnd > firstLineEnd) {
                text = text.substring(firstLineEnd + 1, fenceEnd).trim();
            }
        }
        if (text.startsWith("{") && text.endsWith("}")) {
            return text;
        }

        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private ArrayNode extractAccounts(ObjectNode root) {
        JsonNode accountsNode = root.get("accounts");
        if (accountsNode == null || !accountsNode.isArray()) {
            throw new AiException("AI返回格式异常：缺少accounts数组");
        }
        ArrayNode accounts = (ArrayNode) accountsNode;
        for (JsonNode account : accounts) {
            validateRiskFields(account);
        }
        return accounts;
    }

    private void validateRiskFields(JsonNode account) {
        if (!account.isObject()) {
            throw new AiException("AI返回格式异常：accounts中存在非对象数据");
        }

        AccountRiskAnalysisDTO dto;
        try {
            dto = OBJECT_MAPPER.treeToValue(account, AccountRiskAnalysisDTO.class);
        } catch (Exception e) {
            throw new AiException("AI返回格式异常：账号风险字段解析失败");
        }

        if (!isValidRiskLevel(dto.getRiskLevel())) {
            throw new AiException("AI返回格式异常：risk_level不合法");
        }
        if (dto.getRiskScore() == null) {
            throw new AiException("AI返回格式异常：risk_score不合法");
        }
        if (dto.getRiskTags() == null) {
            throw new AiException("AI返回格式异常：risk_tags必须是数组");
        }
        if (dto.getResult() == null) {
            throw new AiException("AI返回格式异常：result必须是字符串");
        }
        if (dto.getSuggestions() == null) {
            throw new AiException("AI返回格式异常：suggestions必须是数组");
        }
    }

    private boolean isValidRiskLevel(String value) {
        return "HIGH".equals(value) || "MEDIUM".equals(value) || "LOW".equals(value) || "NONE".equals(value);
    }

    private void syncShadowAccounts(ArrayNode analyzedAccounts) {
        for (JsonNode account : analyzedAccounts) {
            if (!account.isObject()) {
                continue;
            }
            JsonNode shadowAccounts = account.get("shadow_accounts");
            if (shadowAccounts == null || !shadowAccounts.isArray()) {
                continue;
            }
            for (JsonNode shadowAccount : shadowAccounts) {
                if (shadowAccount instanceof ObjectNode shadowObject) {
                    shadowObject.set("risk_level", account.get("risk_level"));
                    shadowObject.set("risk_score", account.get("risk_score"));
                    shadowObject.set("risk_tags", account.get("risk_tags"));
                    shadowObject.set("result", account.get("result"));
                    shadowObject.set("suggestions", account.get("suggestions"));
                }
            }
        }
    }

    private String preview(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= ERROR_PREVIEW_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, ERROR_PREVIEW_LENGTH) + "...";
    }
}
