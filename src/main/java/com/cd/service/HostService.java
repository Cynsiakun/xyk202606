package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.AssetProbeDTO;
import com.cd.dto.PortScanDTO;
import com.cd.dto.CsvImportResultDTO;
import com.cd.dto.HostCreateDTO;
import com.cd.dto.HostResponseDTO;
import com.cd.dto.HostUpdateDTO;
import com.cd.entity.HostEntity;
import org.springframework.web.multipart.MultipartFile;

public interface HostService {

    HostResponseDTO create(HostCreateDTO dto);

    HostResponseDTO update(Long id, HostUpdateDTO dto);

    void deleteById(Long id);

    HostResponseDTO getById(Long id);

    PageResult<HostResponseDTO> list(int page, int size, String keyword);

    CsvImportResultDTO importCsv(MultipartFile file);

    void saveOrUpdateFromMessage(HostEntity entity);

    void heartbeat(String macAddress);

    int markOfflineHosts(int offlineThresholdSeconds);

    int autoProbeOnlineHosts(int limit);

    int autoProbeOnlineHosts(int limit, boolean account, boolean service, boolean process, boolean app);

    int autoProbeOnlineHosts(int limit,
                             boolean account,
                             boolean service,
                             boolean process,
                             boolean app,
                             boolean portScan,
                             boolean fingerprint);

    void sendAssetProbe(AssetProbeDTO dto);

    int autoPortScanOnlineHosts(int limit);

    void sendPortScan(PortScanDTO dto);
}
