package com.cd.common.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private String apiKey;
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String model = "qwen3.7-max";
    private Double temperature = 0.2;
    private Integer maxTokens = 1024;
    private AssetAnalysis assetAnalysis = new AssetAnalysis();

    @Data
    public static class AssetAnalysis {
        private Integer serviceBatchSize = 25;
        private Integer processBatchSize = 25;
        private Integer appBatchSize = 25;
    }
}
