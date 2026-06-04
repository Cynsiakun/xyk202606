package com.cd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 资产探测请求：前端勾选探测项并提交目标主机 MAC，后端据此组装 MQ 消息。
 *
 * <p>各布尔字段为是否探测对应资产；未传时按 {@code false}（不探测）处理。最终下发的
 * 消息会把布尔转换为 {@code 1/0} 并固定 {@code type=assets}，字段名不可更改。</p>
 */
@Data
public class AssetProbeDTO {

    /** 探测账号。 */
    private boolean account;

    /** 探测服务。 */
    private boolean service;

    /** 探测进程。 */
    private boolean process;

    /** 探测安装的软件。 */
    private boolean app;

    @NotBlank(message = "MAC地址不能为空")
    @Size(max = 64, message = "macAddress 长度不能超过64")
    private String macAddress;
}
