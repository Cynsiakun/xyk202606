package com.cd.service.impl;

import com.cd.common.ai.AiException;
import com.cd.common.ai.AiProperties;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.dto.AiChatResponseDTO;
import com.cd.dto.AssetRecordDTO;
import com.cd.dto.AssetRiskAnalysisDTO;
import com.cd.entity.AccountEntity;
import com.cd.entity.AppEntity;
import com.cd.entity.ProcessEntity;
import com.cd.entity.ServiceEntity;
import com.cd.mapper.AccountMapper;
import com.cd.mapper.AppMapper;
import com.cd.mapper.ProcessMapper;
import com.cd.mapper.ServiceMapper;
import com.cd.service.AiService;
import com.cd.service.AssetAiAnalysisService;
import com.cd.service.AssetQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetAiAnalysisServiceImpl implements AssetAiAnalysisService {

    private static final String ACCOUNT_PROMPT = "asset-account-analysis";
    private static final String SERVICE_PROMPT = "asset-service-analysis";
    private static final String PROCESS_PROMPT = "asset-process-analysis";
    private static final String APP_PROMPT = "asset-app-analysis";
    private static final int ASSET_ANALYSIS_MAX_TOKENS = 8192;
    private static final int DEFAULT_SERVICE_ANALYSIS_BATCH_SIZE = 25;
    private static final int DEFAULT_PROCESS_ANALYSIS_BATCH_SIZE = 25;
    private static final int DEFAULT_APP_ANALYSIS_BATCH_SIZE = 25;
    private static final int ERROR_PREVIEW_LENGTH = 300;
    private static final String[] SERVICE_COMPACT_FIELDS = {"name", "displayName", "status", "startType", "pid", "binpath", "username"};
    private static final String[] PROCESS_COMPACT_FIELDS = {"pid", "name", "username", "exe"};
    private static final String[] APP_COMPACT_FIELDS = {"name", "version", "publisher", "installDate"};
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AccountMapper accountMapper;
    private final ServiceMapper serviceMapper;
    private final ProcessMapper processMapper;
    private final AppMapper appMapper;
    private final AiService aiService;
    private final AssetQueryService assetQueryService;
    private final AiProperties aiProperties;

    @Override
    public AssetRecordDTO analyzeAccount(Long id, String assetJson) {
        AccountEntity entity = accountMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("账号资产记录不存在 id=" + id);
        }

        return analyzeAsset(AssetAnalysisConfig.<AccountEntity>builder()
                .assetType("account")
                .assetLabel("账号")
                .arrayField("accounts")
                .countField("account_count")
                .promptName(ACCOUNT_PROMPT)
                .entity(entity)
                .overrideAssetJson(assetJson)
                .taskIdGetter(AccountEntity::getTaskId)
                .hostNameGetter(AccountEntity::getHostName)
                .macAddressGetter(AccountEntity::getMacAddress)
                .assetJsonGetter(AccountEntity::getAssetJson)
                .writeBack(items -> writeBackAssetJson(
                        "account",
                        entity.getId(),
                        items,
                        accountMapper.updateAssetJsonById(entity.getId(), items.toString(), items.size())
                ))
                .afterExtract(this::syncShadowAccounts)
                .detailSupplier(() -> assetQueryService.accountDetail(id))
                .build());
    }

    @Override
    public AssetRecordDTO analyzeService(Long id, String assetJson) {
        ServiceEntity entity = serviceMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("服务资产记录不存在 id=" + id);
        }

        return analyzeAsset(AssetAnalysisConfig.<ServiceEntity>builder()
                .assetType("service")
                .assetLabel("服务")
                .arrayField("services")
                .countField("service_count")
                .promptName(SERVICE_PROMPT)
                .entity(entity)
                .overrideAssetJson(assetJson)
                .taskIdGetter(ServiceEntity::getTaskId)
                .hostNameGetter(ServiceEntity::getHostName)
                .macAddressGetter(ServiceEntity::getMacAddress)
                .assetJsonGetter(ServiceEntity::getAssetJson)
                .writeBack(items -> writeBackAssetJson(
                        "service",
                        entity.getId(),
                        items,
                        serviceMapper.updateAssetJsonById(entity.getId(), items.toString(), items.size())
                ))
                .batchSize(serviceBatchSize())
                .compactPayload(true)
                .compactFields(SERVICE_COMPACT_FIELDS)
                .detailSupplier(() -> assetQueryService.serviceDetail(id))
                .build());
    }

    @Override
    public AssetRecordDTO analyzeProcess(Long id, String assetJson) {
        ProcessEntity entity = processMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("进程资产记录不存在 id=" + id);
        }

        return analyzeAsset(AssetAnalysisConfig.<ProcessEntity>builder()
                .assetType("process")
                .assetLabel("进程")
                .arrayField("processes")
                .countField("process_count")
                .promptName(PROCESS_PROMPT)
                .entity(entity)
                .overrideAssetJson(assetJson)
                .taskIdGetter(ProcessEntity::getTaskId)
                .hostNameGetter(ProcessEntity::getHostName)
                .macAddressGetter(ProcessEntity::getMacAddress)
                .assetJsonGetter(ProcessEntity::getAssetJson)
                .writeBack(items -> writeBackAssetJson(
                        "process",
                        entity.getId(),
                        items,
                        processMapper.updateAssetJsonById(entity.getId(), items.toString(), items.size())
                ))
                .batchSize(processBatchSize())
                .compactPayload(true)
                .compactFields(PROCESS_COMPACT_FIELDS)
                .detailSupplier(() -> assetQueryService.processDetail(id))
                .build());
    }

    @Override
    public AssetRecordDTO analyzeApp(Long id, String assetJson) {
        AppEntity entity = appMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("APP资产记录不存在 id=" + id);
        }

        return analyzeAsset(AssetAnalysisConfig.<AppEntity>builder()
                .assetType("app")
                .assetLabel("APP")
                .arrayField("apps")
                .countField("app_count")
                .promptName(APP_PROMPT)
                .entity(entity)
                .overrideAssetJson(assetJson)
                .taskIdGetter(AppEntity::getTaskId)
                .hostNameGetter(AppEntity::getHostName)
                .macAddressGetter(AppEntity::getMacAddress)
                .assetJsonGetter(AppEntity::getAssetJson)
                .writeBack(items -> writeBackAssetJson(
                        "app",
                        entity.getId(),
                        items,
                        appMapper.updateAssetJsonById(entity.getId(), items.toString(), items.size())
                ))
                .batchSize(appBatchSize())
                .compactPayload(true)
                .compactFields(APP_COMPACT_FIELDS)
                .detailSupplier(() -> assetQueryService.appDetail(id))
                .build());
    }

    private int serviceBatchSize() {
        Integer configured = aiProperties.getAssetAnalysis() == null
                ? null
                : aiProperties.getAssetAnalysis().getServiceBatchSize();
        return configured != null && configured > 0 ? configured : DEFAULT_SERVICE_ANALYSIS_BATCH_SIZE;
    }

    private int processBatchSize() {
        Integer configured = aiProperties.getAssetAnalysis() == null
                ? null
                : aiProperties.getAssetAnalysis().getProcessBatchSize();
        return configured != null && configured > 0 ? configured : DEFAULT_PROCESS_ANALYSIS_BATCH_SIZE;
    }

    private int appBatchSize() {
        Integer configured = aiProperties.getAssetAnalysis() == null
                ? null
                : aiProperties.getAssetAnalysis().getAppBatchSize();
        return configured != null && configured > 0 ? configured : DEFAULT_APP_ANALYSIS_BATCH_SIZE;
    }

    private <T> AssetRecordDTO analyzeAsset(AssetAnalysisConfig<T> config) {
        String sourceAssetJson = config.compactPayload
                ? config.assetJsonGetter.apply(config.entity)
                : (StringUtils.hasText(config.overrideAssetJson) ? config.overrideAssetJson : config.assetJsonGetter.apply(config.entity));
        ArrayNode items = readAssetItems(
                sourceAssetJson,
                config.assetLabel
        );
        AssetAnalysisStats stats = config.compactPayload ? new AssetAnalysisStats(config.assetType) : null;
        ArrayNode analyzedItems = config.compactPayload
                ? analyzeCompactAssetItems(config, items, stats)
                : analyzeAssetItems(config, items, stats);
        if (config.afterExtract != null) {
            config.afterExtract.accept(analyzedItems);
        }

        config.writeBack.accept(analyzedItems);
        if (stats != null) {
            stats.log();
        }
        return config.detailSupplier.get();
    }

    private void writeBackAssetJson(String assetType, Long id, ArrayNode items, int updatedRows) {
        if (updatedRows <= 0) {
            throw new AiException("AI分析结果写入数据库失败：" + assetType + "资产记录不存在或已删除 id=" + id);
        }
        log.info("AI分析结果已写入数据库: type={}, id={}, count={}, assetJsonBytes={}",
                assetType,
                id,
                items.size(),
                utf8Bytes(items.toString()));
    }

    private <T> ArrayNode analyzeCompactAssetItems(AssetAnalysisConfig<T> config,
                                                   ArrayNode allItems,
                                                   AssetAnalysisStats stats) {
        int pendingCount = countPendingRiskItems(allItems);
        if (pendingCount == 0) {
            log.info("资产AI分析跳过: type={}, total={}, pending=0, 已存在完整风险字段", config.assetType, allItems.size());
            return allItems;
        }

        ArrayNode currentItems = allItems.deepCopy();
        int batchSize = config.batchSize <= 0 ? pendingCount : config.batchSize;
        for (int start = 0; start < allItems.size(); ) {
            ArrayNode batch = OBJECT_MAPPER.createArrayNode();
            int scan = start;
            while (scan < allItems.size() && batch.size() < batchSize) {
                JsonNode item = currentItems.get(scan);
                if (!hasCompleteRiskFields(item)) {
                    ObjectNode itemCopy = ((ObjectNode) item).deepCopy();
                    itemCopy.put("_asset_index", scan);
                    batch.add(itemCopy);
                }
                scan++;
            }
            if (batch.isEmpty()) {
                start = scan;
                continue;
            }

            ArrayNode analyzedBatch = analyzeAssetBatch(config, batch, start, stats);
            mergeAnalyzedBatchByIndex(currentItems, analyzedBatch, config);
            config.writeBack.accept(currentItems);
            start = scan;
        }
        return currentItems;
    }

    private <T> ArrayNode analyzeAssetItems(AssetAnalysisConfig<T> config, ArrayNode items, AssetAnalysisStats stats) {
        if (config.batchSize <= 0 || items.size() <= config.batchSize) {
            return analyzeAssetBatch(config, items, 0, stats);
        }

        ArrayNode merged = OBJECT_MAPPER.createArrayNode();
        for (int start = 0; start < items.size(); start += config.batchSize) {
            ArrayNode batch = OBJECT_MAPPER.createArrayNode();
            int end = Math.min(start + config.batchSize, items.size());
            for (int i = start; i < end; i++) {
                batch.add(items.get(i));
            }
            ArrayNode analyzedBatch = analyzeAssetBatch(config, batch, start, stats);
            if (analyzedBatch.size() != batch.size()) {
                throw new AiException("AI返回格式异常：" + config.assetLabel + "第" + (start + 1) + "-"
                        + end + "条返回数量不一致");
            }
            merged.addAll(analyzedBatch);
        }
        return merged;
    }

    private <T> ArrayNode analyzeAssetBatch(AssetAnalysisConfig<T> config,
                                            ArrayNode items,
                                            int startIndex,
                                            AssetAnalysisStats stats) {
        ObjectNode oldProtocolRequest = stats == null ? null : buildAssetUserMessage(config, items);
        ObjectNode userMessage = config.compactPayload
                ? buildCompactServiceUserMessage(config, items, startIndex)
                : buildAssetUserMessage(config, items);
        if (stats != null) {
            stats.recordRequest(oldProtocolRequest.toString(), userMessage.toString());
            stats.recordCall();
        }
        AiChatResponseDTO aiResponse = aiService.chatJsonWithSystem(
                config.promptName,
                userMessage.toString(),
                ASSET_ANALYSIS_MAX_TOKENS
        );
        try {
            ObjectNode analyzedRoot = readAiJson(aiResponse.getContent());
            ArrayNode analyzedItems = extractAssetItems(analyzedRoot, config);
            if (!config.compactPayload) {
                return analyzedItems;
            }
            ArrayNode mergedItems = mergeCompactRiskResults(items, analyzedItems, startIndex, config);
            if (stats != null) {
                stats.recordResponse(buildAssetUserMessage(config, mergedItems).toString(), aiResponse.getContent());
            }
            return mergedItems;
        } catch (AiException e) {
            if (startIndex <= 0 && (config.batchSize <= 0 || items.size() <= config.batchSize)) {
                throw e;
            }
            throw new AiException("AI分析失败：" + config.assetLabel + "第" + (startIndex + 1) + "-"
                    + (startIndex + items.size()) + "条返回异常：" + e.getMessage());
        }
    }

    private <T> ObjectNode buildCompactServiceUserMessage(AssetAnalysisConfig<T> config, ArrayNode items, int startIndex) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("taskId", config.taskIdGetter.apply(config.entity));
        root.put("type", config.assetType);
        root.put("hostName", config.hostNameGetter.apply(config.entity));
        root.put("macAddress", config.macAddressGetter.apply(config.entity));

        ArrayNode compactItems = OBJECT_MAPPER.createArrayNode();
        for (int i = 0; i < items.size(); i++) {
            JsonNode item = items.get(i);
            ObjectNode compact = OBJECT_MAPPER.createObjectNode();
            JsonNode assetIndex = item.get("_asset_index");
            compact.put("index", assetIndex != null && assetIndex.canConvertToInt() ? assetIndex.asInt() : startIndex + i);
            for (String fieldName : config.compactFields) {
                copyIfPresent(compact, item, fieldName);
            }
            compactItems.add(compact);
        }

        root.set(config.arrayField, compactItems);
        root.put(config.countField, compactItems.size());
        return root;
    }

    private void copyIfPresent(ObjectNode target, JsonNode source, String fieldName) {
        JsonNode value = source == null ? null : source.get(fieldName);
        if (value != null && !value.isMissingNode()) {
            target.set(fieldName, value);
        }
    }

    private <T> ArrayNode mergeCompactRiskResults(ArrayNode originalItems,
                                                  ArrayNode riskItems,
                                                  int startIndex,
                                                  AssetAnalysisConfig<T> config) {
        Map<Integer, ObjectNode> riskByIndex = new HashMap<>();
        for (JsonNode riskItem : riskItems) {
            JsonNode indexNode = riskItem.get("index");
            if (indexNode == null || !indexNode.canConvertToInt()) {
                throw new AiException("AI返回格式异常：" + config.assetLabel + "风险结果缺少index");
            }
            int index = indexNode.asInt();
            if (!containsAssetIndex(originalItems, index, startIndex)) {
                throw new AiException("AI返回格式异常：" + config.assetLabel + "风险结果index越界");
            }
            if (riskByIndex.put(index, (ObjectNode) riskItem) != null) {
                throw new AiException("AI返回格式异常：" + config.assetLabel + "风险结果index重复");
            }
        }

        ArrayNode merged = OBJECT_MAPPER.createArrayNode();
        for (int i = 0; i < originalItems.size(); i++) {
            JsonNode original = originalItems.get(i);
            int index = original.has("_asset_index") ? original.get("_asset_index").asInt() : startIndex + i;
            ObjectNode riskItem = riskByIndex.get(index);
            if (riskItem == null) {
                throw new AiException("AI返回格式异常：" + config.assetLabel + "风险结果缺少index=" + index);
            }
            if (!(original instanceof ObjectNode originalObject)) {
                throw new AiException("AI返回格式异常：" + config.arrayField + "中存在非对象数据");
            }
            ObjectNode mergedObject = originalObject.deepCopy();
            mergedObject.set("risk_level", riskItem.get("risk_level"));
            mergedObject.set("risk_score", riskItem.get("risk_score"));
            mergedObject.set("risk_tags", riskItem.get("risk_tags"));
            mergedObject.set("result", riskItem.get("result"));
            mergedObject.set("suggestions", riskItem.get("suggestions"));
            merged.add(mergedObject);
        }
        return merged;
    }

    private int countPendingRiskItems(ArrayNode items) {
        int count = 0;
        for (JsonNode item : items) {
            if (!hasCompleteRiskFields(item)) {
                count++;
            }
        }
        return count;
    }

    private boolean hasCompleteRiskFields(JsonNode item) {
        if (item == null || !item.isObject()) {
            return false;
        }
        return isValidRiskLevel(textValue(item.get("risk_level")))
                && item.get("risk_score") != null
                && item.get("risk_score").canConvertToInt()
                && item.get("risk_tags") != null
                && item.get("risk_tags").isArray()
                && item.get("result") != null
                && item.get("result").isTextual()
                && item.get("suggestions") != null
                && item.get("suggestions").isArray();
    }

    private String textValue(JsonNode node) {
        return node == null || !node.isTextual() ? null : node.asText();
    }

    private boolean containsAssetIndex(ArrayNode items, int index, int startIndex) {
        for (int i = 0; i < items.size(); i++) {
            JsonNode item = items.get(i);
            JsonNode assetIndex = item.get("_asset_index");
            int currentIndex = assetIndex != null && assetIndex.canConvertToInt() ? assetIndex.asInt() : startIndex + i;
            if (currentIndex == index) {
                return true;
            }
        }
        return false;
    }

    private <T> void mergeAnalyzedBatchByIndex(ArrayNode currentItems,
                                               ArrayNode analyzedBatch,
                                               AssetAnalysisConfig<T> config) {
        for (JsonNode analyzedItem : analyzedBatch) {
            JsonNode indexNode = analyzedItem.get("_asset_index");
            if (indexNode == null || !indexNode.canConvertToInt()) {
                throw new AiException("AI返回格式异常：" + config.assetLabel + "合并结果缺少内部index");
            }
            int index = indexNode.asInt();
            if (index < 0 || index >= currentItems.size()) {
                throw new AiException("AI返回格式异常：" + config.assetLabel + "合并结果index越界");
            }
            ObjectNode cleanItem = ((ObjectNode) analyzedItem).deepCopy();
            cleanItem.remove("_asset_index");
            currentItems.set(index, cleanItem);
        }
    }

    private ArrayNode readAssetItems(String assetJson, String assetLabel) {
        if (!StringUtils.hasText(assetJson)) {
            throw new IllegalArgumentException(assetLabel + "资产数据为空");
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(assetJson);
            if (!node.isArray()) {
                throw new AiException("AI分析失败：" + assetLabel + "资产数据必须是数组");
            }
            return (ArrayNode) node;
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(assetLabel + "资产JSON格式异常");
        }
    }

    private <T> ObjectNode buildAssetUserMessage(AssetAnalysisConfig<T> config, ArrayNode items) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("taskId", config.taskIdGetter.apply(config.entity));
        root.put("type", config.assetType);
        root.put("hostName", config.hostNameGetter.apply(config.entity));
        root.put("macAddress", config.macAddressGetter.apply(config.entity));
        root.set(config.arrayField, items);
        root.put(config.countField, items.size());
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

    private <T> ArrayNode extractAssetItems(ObjectNode root, AssetAnalysisConfig<T> config) {
        JsonNode itemsNode = root.get(config.arrayField);
        if (itemsNode == null || !itemsNode.isArray()) {
            throw new AiException("AI返回格式异常：缺少" + config.arrayField + "数组");
        }
        ArrayNode items = (ArrayNode) itemsNode;
        for (JsonNode item : items) {
            validateRiskFields(item, config);
        }
        return items;
    }

    private <T> void validateRiskFields(JsonNode item, AssetAnalysisConfig<T> config) {
        if (!item.isObject()) {
            throw new AiException("AI返回格式异常：" + config.arrayField + "中存在非对象数据");
        }

        AssetRiskAnalysisDTO dto;
        try {
            dto = OBJECT_MAPPER.treeToValue(item, AssetRiskAnalysisDTO.class);
        } catch (Exception e) {
            throw new AiException("AI返回格式异常：" + config.assetLabel + "风险字段解析失败");
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

    private static int utf8Bytes(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static class AssetAnalysisConfig<T> {
        private String assetType;
        private String assetLabel;
        private String arrayField;
        private String countField;
        private String promptName;
        private T entity;
        private String overrideAssetJson;
        private Function<T, String> taskIdGetter;
        private Function<T, String> hostNameGetter;
        private Function<T, String> macAddressGetter;
        private Function<T, String> assetJsonGetter;
        private Consumer<ArrayNode> writeBack;
        private Consumer<ArrayNode> afterExtract;
        private Supplier<AssetRecordDTO> detailSupplier;
        private int batchSize;
        private boolean compactPayload;
        private String[] compactFields = new String[0];

        private static <T> Builder<T> builder() {
            return new Builder<>();
        }
    }

    private static class Builder<T> {
        private final AssetAnalysisConfig<T> config = new AssetAnalysisConfig<>();

        private Builder<T> assetType(String assetType) {
            config.assetType = assetType;
            return this;
        }

        private Builder<T> assetLabel(String assetLabel) {
            config.assetLabel = assetLabel;
            return this;
        }

        private Builder<T> arrayField(String arrayField) {
            config.arrayField = arrayField;
            return this;
        }

        private Builder<T> countField(String countField) {
            config.countField = countField;
            return this;
        }

        private Builder<T> promptName(String promptName) {
            config.promptName = promptName;
            return this;
        }

        private Builder<T> entity(T entity) {
            config.entity = entity;
            return this;
        }

        private Builder<T> overrideAssetJson(String overrideAssetJson) {
            config.overrideAssetJson = overrideAssetJson;
            return this;
        }

        private Builder<T> taskIdGetter(Function<T, String> taskIdGetter) {
            config.taskIdGetter = taskIdGetter;
            return this;
        }

        private Builder<T> hostNameGetter(Function<T, String> hostNameGetter) {
            config.hostNameGetter = hostNameGetter;
            return this;
        }

        private Builder<T> macAddressGetter(Function<T, String> macAddressGetter) {
            config.macAddressGetter = macAddressGetter;
            return this;
        }

        private Builder<T> assetJsonGetter(Function<T, String> assetJsonGetter) {
            config.assetJsonGetter = assetJsonGetter;
            return this;
        }

        private Builder<T> writeBack(Consumer<ArrayNode> writeBack) {
            config.writeBack = writeBack;
            return this;
        }

        private Builder<T> afterExtract(Consumer<ArrayNode> afterExtract) {
            config.afterExtract = afterExtract;
            return this;
        }

        private Builder<T> detailSupplier(Supplier<AssetRecordDTO> detailSupplier) {
            config.detailSupplier = detailSupplier;
            return this;
        }

        private Builder<T> batchSize(int batchSize) {
            config.batchSize = batchSize;
            return this;
        }

        private Builder<T> compactPayload(boolean compactPayload) {
            config.compactPayload = compactPayload;
            return this;
        }

        private Builder<T> compactFields(String[] compactFields) {
            config.compactFields = compactFields == null ? new String[0] : compactFields;
            return this;
        }

        private AssetAnalysisConfig<T> build() {
            return config;
        }
    }

    private static class AssetAnalysisStats {
        private final String assetType;
        private long oldRequestBytes;
        private long newRequestBytes;
        private long oldResponseBytes;
        private long newResponseBytes;
        private int aiCalls;

        private AssetAnalysisStats(String assetType) {
            this.assetType = assetType;
        }

        private void recordRequest(String oldProtocolRequest, String newProtocolRequest) {
            oldRequestBytes += utf8Bytes(oldProtocolRequest);
            newRequestBytes += utf8Bytes(newProtocolRequest);
        }

        private void recordResponse(String oldProtocolResponse, String newProtocolResponse) {
            oldResponseBytes += utf8Bytes(oldProtocolResponse);
            newResponseBytes += utf8Bytes(newProtocolResponse);
        }

        private void recordCall() {
            aiCalls++;
        }

        private void log() {
            long oldTotal = oldRequestBytes + oldResponseBytes;
            long newTotal = newRequestBytes + newResponseBytes;
            double reduction = oldTotal <= 0 ? 0 : (1 - (double) newTotal / oldTotal) * 100;
            log.info("资产AI分析Token优化统计: type={}, 请求大小 {} -> {} bytes, 响应大小 {} -> {} bytes, AI调用次数 {} -> {}, 预计Token减少比例 {}%",
                    assetType,
                    oldRequestBytes,
                    newRequestBytes,
                    oldResponseBytes,
                    newResponseBytes,
                    aiCalls,
                    aiCalls,
                    String.format("%.1f", reduction));
        }

    }
}
