package com.cd.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccountRiskAnalysisDTO {

    @JsonProperty("risk_level")
    private String riskLevel;

    @JsonProperty("risk_score")
    private Integer riskScore;

    @JsonProperty("risk_tags")
    private List<String> riskTags;

    private String result;

    private List<String> suggestions;
}
