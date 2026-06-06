package com.cd.controller;

import com.cd.common.Result;
import com.cd.dto.AiChatResponseDTO;
import com.cd.dto.AiTestRequestDTO;
import com.cd.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PreAuthorize("@perm.has('asset:view')")
    @GetMapping("/test")
    public Result<AiChatResponseDTO> test() {
        return Result.success(aiService.chat("test", Map.of("question", "请用一句话介绍你能为资产安全分析做什么。")));
    }

    @PreAuthorize("@perm.has('asset:view')")
    @PostMapping("/test")
    public Result<AiChatResponseDTO> test(@RequestBody(required = false) AiTestRequestDTO dto) {
        String question = dto == null || !StringUtils.hasText(dto.getQuestion())
                ? "请用一句话介绍你能为资产安全分析做什么。"
                : dto.getQuestion();
        return Result.success(aiService.chat("test", Map.of("question", question)));
    }
}
