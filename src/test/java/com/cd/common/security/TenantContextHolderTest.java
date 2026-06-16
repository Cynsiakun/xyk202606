package com.cd.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TenantContextHolderTest {

    @Test
    void shouldSetGetAndClearTenantId() {
        TenantContextHolder.setTenantId(0L);

        assertEquals(0L, TenantContextHolder.getTenantId());

        TenantContextHolder.clear();
        assertNull(TenantContextHolder.getTenantId());
    }
}
