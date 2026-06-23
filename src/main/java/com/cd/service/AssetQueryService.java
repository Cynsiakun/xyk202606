package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.AssetOverviewDTO;
import com.cd.dto.AssetRecordDTO;

/**
 * 资产数据查询服务：总览 + 四类资产分页列表 + 详情 + 逻辑删除。
 *
 * <p>所有方法均接收 hostScope 参数，当前阶段仅预留，不参与过滤逻辑。</p>
 */
public interface AssetQueryService {

    // ——— 总览 ———
    PageResult<AssetOverviewDTO> overview(int page, int size, String keyword, String hostScope);

    /**
     * 按 MAC 取某类资产的最新一条记录（含 assetJson），用于主机维度的资产查看弹窗。
     * 无记录时返回 null。
     *
     * @param assetType account / service / process / app / port
     */
    AssetRecordDTO latestByMac(String assetType, String macAddress);

    // ——— 账号资产 ———
    PageResult<AssetRecordDTO> accountList(int page, int size, String keyword, String hostScope);

    AssetRecordDTO accountDetail(Long id);

    void deleteAccount(Long id);

    // ——— 服务资产 ———
    PageResult<AssetRecordDTO> serviceList(int page, int size, String keyword, String hostScope);

    AssetRecordDTO serviceDetail(Long id);

    void deleteService(Long id);

    // ——— 进程资产 ———
    PageResult<AssetRecordDTO> processList(int page, int size, String keyword, String hostScope);

    AssetRecordDTO processDetail(Long id);

    void deleteProcess(Long id);

    // ——— APP资产 ———
    PageResult<AssetRecordDTO> appList(int page, int size, String keyword, String hostScope);

    AssetRecordDTO appDetail(Long id);

    void deleteApp(Long id);

    PageResult<AssetRecordDTO> portList(int page, int size, String keyword, String hostScope);

    AssetRecordDTO portDetail(Long id);

    void deletePort(Long id);

    int rematchPort(Long id);

    int rematchLatestPortByMac(String macAddress);
}
