package com.cd.common.security;
public final class TenantContextHolder {
    private static final ThreadLocal<Long> TENANT_ID_HOLDER = new ThreadLocal<>();
    private TenantContextHolder() {
    }
    public static void setTenantId(Long tenantId) {
        TENANT_ID_HOLDER.set(tenantId);
    }
    public static Long getTenantId() {
        return TENANT_ID_HOLDER.get();
    }
    public static void clear() {
        TENANT_ID_HOLDER.remove();
    }
}
