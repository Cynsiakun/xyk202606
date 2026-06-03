package com.cd.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 统一的权限判断入口，供 {@code @PreAuthorize("@perm.has('xxx')")} 使用。
 *
 * <p>超级管理员（拥有 {@code ROLE_SUPER_ADMIN} 或通配权限 {@code *}）一律放行，
 * 无需在数据库里逐条授予权限；其余用户按权限码精确匹配。这样超管逻辑只存在这一处。</p>
 */
@Component("perm")
public class PermissionChecker {

    private static final String SUPER_ADMIN_AUTHORITY = "ROLE_SUPER_ADMIN";
    private static final String WILDCARD = "*";

    /** 当前登录用户是否拥有指定权限码（超级管理员通配放行）。 */
    public boolean has(String permissionCode) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority.getAuthority();
            if (SUPER_ADMIN_AUTHORITY.equals(value) || WILDCARD.equals(value)) {
                return true;
            }
            if (permissionCode != null && permissionCode.equals(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回当前登录用户的权限码集合，供前端做按钮级控制。
     * 超级管理员返回 {@code ["*"]} 表示拥有全部权限。
     */
    public List<String> currentPermissionCodes() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return List.of();
        }
        if (isSuperAdmin()) {
            return List.of(WILDCARD);
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(value -> !value.startsWith("ROLE_"))
                .toList();
    }

    /** 当前登录用户是否为超级管理员（或拥有通配权限）。 */
    public boolean isSuperAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String value = authority.getAuthority();
            if (SUPER_ADMIN_AUTHORITY.equals(value) || WILDCARD.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
