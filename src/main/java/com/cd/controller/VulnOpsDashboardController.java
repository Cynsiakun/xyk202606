package com.cd.controller;

import com.cd.common.Result;
import com.cd.dto.VulnOpsDashboardResponseDTO;
import com.cd.service.VulnOpsDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Validated
@RestController
@RequestMapping("/api/vuln-ops-dashboard")
@RequiredArgsConstructor
@PreAuthorize("@licenseGuard.hasFeature('VULN')")
public class VulnOpsDashboardController {

    private final VulnOpsDashboardService vulnOpsDashboardService;

    @PreAuthorize("@perm.has('vuln-ops-dashboard:view')")
    @GetMapping("/overview")
    public Result<VulnOpsDashboardResponseDTO> overview(
            @RequestParam(defaultValue = "24h") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return Result.success(vulnOpsDashboardService.overview(range, startDate, endDate));
    }

    @PreAuthorize("@perm.has('vuln-ops-dashboard:view')")
    @GetMapping("/export")
    public ResponseEntity<String> export(
            @RequestParam(defaultValue = "24h") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        String fileName = "vuln-ops-dashboard-" + LocalDate.now() + ".html";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .body(vulnOpsDashboardService.exportHtml(range, startDate, endDate));
    }
}
