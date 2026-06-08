package com.cd.service;

public interface PatchNormalizeService {

    /**
     * 对补丁编号进行统一标准化。
     * <p>
     * 常见标准化结果示例：
     * <ul>
     *     <li>5058379 -> KB5058379</li>
     *     <li>kb5058379 -> KB5058379</li>
     *     <li>KB-5058379 -> KB5058379</li>
     *     <li>Windows10.0-KB5058379-x64 -> KB5058379</li>
     * </ul>
     *
     * @param patchId 原始补丁编号
     * @return 标准化后的补丁编号；若输入为空则返回 null
     */
    String normalizePatchId(String patchId);
}
