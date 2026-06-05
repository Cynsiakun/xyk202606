package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.dto.AssetOverviewDTO;
import com.cd.dto.AssetRecordDTO;
import com.cd.service.AssetQueryService;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资产数据查询接口。
 *
 * <p>所有列表接口统一返回 {@link PageResult}，详情接口返回含 assetJson 的 {@link AssetRecordDTO}。
 * hostScope 参数当前阶段预留，后续用于按用户范围过滤。</p>
 */
@Validated
@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetQueryService assetQueryService;

    // ——— 总览 ———
    @PreAuthorize("@perm.has('asset:view')")
    @GetMapping("/overview")
    public Result<PageResult<AssetOverviewDTO>> overview(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String hostScope) {
        return Result.success(assetQueryService.overview(page, size, keyword, hostScope));
    }

    // ——— 主机维度：按 MAC 取某类资产最新记录（普通用户凭 host:asset:view 即可查看） ———
    @PreAuthorize("@perm.has('host:asset:view') or @perm.has('asset:view')")
    @GetMapping("/host-latest")
    public Result<AssetRecordDTO> hostLatest(
            @RequestParam String type,
            @RequestParam String mac) {
        return Result.success(assetQueryService.latestByMac(type, mac));
    }

    // ——— 账号资产 ———
    @PreAuthorize("@perm.has('asset:view')")
    @GetMapping("/account/list")
    public Result<PageResult<AssetRecordDTO>> accountList(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String hostScope) {
        return Result.success(assetQueryService.accountList(page, size, keyword, hostScope));
    }

    @PreAuthorize("@perm.has('asset:view')")
    @GetMapping("/account/{id}")
    public Result<AssetRecordDTO> accountDetail(@PathVariable @Min(1) Long id) {
        return Result.success(assetQueryService.accountDetail(id));
    }

    @PreAuthorize("@perm.has('asset:delete')")
    @DeleteMapping("/account/{id}")
    public Result<Void> deleteAccount(@PathVariable @Min(1) Long id) {
        assetQueryService.deleteAccount(id);
        return Result.success();
    }

    // ——— 服务资产 ———
    @PreAuthorize("@perm.has('asset:view')")
    @GetMapping("/service/list")
    public Result<PageResult<AssetRecordDTO>> serviceList(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String hostScope) {
        return Result.success(assetQueryService.serviceList(page, size, keyword, hostScope));
    }

    @PreAuthorize("@perm.has('asset:view')")
    @GetMapping("/service/{id}")
    public Result<AssetRecordDTO> serviceDetail(@PathVariable @Min(1) Long id) {
        return Result.success(assetQueryService.serviceDetail(id));
    }

    @PreAuthorize("@perm.has('asset:delete')")
    @DeleteMapping("/service/{id}")
    public Result<Void> deleteService(@PathVariable @Min(1) Long id) {
        assetQueryService.deleteService(id);
        return Result.success();
    }

    // ——— 进程资产 ———
    @PreAuthorize("@perm.has('asset:view')")
    @GetMapping("/process/list")
    public Result<PageResult<AssetRecordDTO>> processList(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String hostScope) {
        return Result.success(assetQueryService.processList(page, size, keyword, hostScope));
    }

    @PreAuthorize("@perm.has('asset:view')")
    @GetMapping("/process/{id}")
    public Result<AssetRecordDTO> processDetail(@PathVariable @Min(1) Long id) {
        return Result.success(assetQueryService.processDetail(id));
    }

    @PreAuthorize("@perm.has('asset:delete')")
    @DeleteMapping("/process/{id}")
    public Result<Void> deleteProcess(@PathVariable @Min(1) Long id) {
        assetQueryService.deleteProcess(id);
        return Result.success();
    }

    // ——— APP资产 ———
    @PreAuthorize("@perm.has('asset:view')")
    @GetMapping("/app/list")
    public Result<PageResult<AssetRecordDTO>> appList(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String hostScope) {
        return Result.success(assetQueryService.appList(page, size, keyword, hostScope));
    }

    @PreAuthorize("@perm.has('asset:view')")
    @GetMapping("/app/{id}")
    public Result<AssetRecordDTO> appDetail(@PathVariable @Min(1) Long id) {
        return Result.success(assetQueryService.appDetail(id));
    }

    @PreAuthorize("@perm.has('asset:delete')")
    @DeleteMapping("/app/{id}")
    public Result<Void> deleteApp(@PathVariable @Min(1) Long id) {
        assetQueryService.deleteApp(id);
        return Result.success();
    }
}
