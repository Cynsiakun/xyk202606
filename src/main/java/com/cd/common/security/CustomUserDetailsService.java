package com.cd.common.security;

import com.cd.entity.UserEntity;
import com.cd.mapper.RbacMapper;
import com.cd.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final UserMapper userMapper;
    private final RbacMapper rbacMapper;

    @Override
    public SecurityUser loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity user = userMapper.selectByUserName(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        return buildSecurityUser(user);
    }

    public SecurityUser loadUserById(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        return buildSecurityUser(user);
    }

    public List<String> loadRoleCodes(Long userId) {
        return rbacMapper.selectRoleCodesByUserId(userId);
    }

    private SecurityUser buildSecurityUser(UserEntity user) {
        List<String> roleCodes = rbacMapper.selectRoleCodesByUserId(user.getId());
        List<String> permissionCodes = new ArrayList<>(rbacMapper.selectPermissionCodesByUserId(user.getId()));
        if (roleCodes.contains(SUPER_ADMIN)) {
            permissionCodes = rbacMapper.selectAllPermissionCodes();
        }

        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (String roleCode : roleCodes) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + roleCode));
        }
        for (String permissionCode : permissionCodes) {
            authorities.add(new SimpleGrantedAuthority(permissionCode));
        }

        return new SecurityUser(
                user.getId(),
                user.getUserName(),
                user.getUserPwd(),
                user.getStatus(),
                authorities
        );
    }
}
