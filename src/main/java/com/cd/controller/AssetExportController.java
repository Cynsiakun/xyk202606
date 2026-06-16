package com.cd.controller;

import com.cd.common.Result;
import com.cd.dto.AssetExportDTO;
import com.cd.dto.AssetExportFileDTO;
import com.cd.service.AssetExportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Validated
@RestController
@RequestMapping("/api/asset/export")
@RequiredArgsConstructor
@PreAuthorize("@licenseGuard.hasFeature('ASSET_EXPORT')")
public class AssetExportController {

    private final AssetExportService assetExportService;

    @PreAuthorize("@perm.has('asset:export')")
    @GetMapping("/{hostId}")
    public Object export(@PathVariable @Min(1) Long hostId,
                         @RequestParam(defaultValue = "json") String format,
                         HttpServletRequest request) {
        String ipAddress = clientIp(request);
        if ("excel".equalsIgnoreCase(format)) {
            AssetExportFileDTO file = assetExportService.exportExcel(hostId, ipAddress);
            String encodedFileName = URLEncoder.encode(file.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                    .body(file.getContent());
        }
        AssetExportDTO exportData = assetExportService.exportJson(hostId, ipAddress);
        return Result.success(exportData);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
