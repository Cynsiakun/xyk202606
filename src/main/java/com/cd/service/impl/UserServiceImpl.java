package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.common.auth.LoginSessionManager;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.common.exception.UnauthorizedException;
import com.cd.dto.UserChangePasswordDTO;
import com.cd.dto.UserCreateDTO;
import com.cd.dto.UserCurrentDTO;
import com.cd.dto.UserLoginDTO;
import com.cd.dto.UserLoginResponseDTO;
import com.cd.dto.UserResponseDTO;
import com.cd.dto.UserUpdateDTO;
import com.cd.dto.UserUpdateSelfDTO;
import com.cd.entity.UserEntity;
import com.cd.mapper.UserMapper;
import com.cd.service.LoginLogService;
import com.cd.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final LoginSessionManager loginSessionManager;
    private final LoginLogService loginLogService;

    @Override
    public UserLoginResponseDTO login(UserLoginDTO dto, String ipAddress) {
        UserEntity user = userMapper.selectByUserName(dto.getUserName());
        if (user == null || !user.getUserPwd().equals(md5(dto.getPassword()))) {
            loginLogService.record(user == null ? null : user.getId(), dto.getUserName(), ipAddress, 0, "用户名或密码错误");
            throw new UnauthorizedException("用户名或密码错误");
        }

        userMapper.updateLastLoginTime(user.getId());
        UserEntity latestUser = userMapper.selectById(user.getId());
        String token = loginSessionManager.createToken(latestUser.getId());
        loginLogService.record(latestUser.getId(), latestUser.getUserName(), ipAddress, 1, "登录成功");

        UserLoginResponseDTO response = new UserLoginResponseDTO();
        response.setToken(token);
        response.setUserId(latestUser.getId());
        response.setUserName(latestUser.getUserName());
        response.setLastLoginTime(latestUser.getLastLoginTime());
        return response;
    }

    @Override
    public UserCurrentDTO currentUser(Long currentUserId) {
        return toCurrentResponse(ensureExists(currentUserId));
    }

    @Override
    public void logout(String token) {
        validateToken(token);
        loginSessionManager.removeToken(token);
    }

    @Override
    public UserCurrentDTO updateSelf(Long currentUserId, UserUpdateSelfDTO dto) {
        UserEntity existing = ensureExists(currentUserId);
        validateUnique(currentUserId, existing.getUserName(), dto.getUserPhone(), dto.getUserEmail());
        existing.setUserAvatar(dto.getUserAvatar());
        existing.setUserPhone(emptyToNull(dto.getUserPhone()));
        existing.setUserEmail(emptyToNull(dto.getUserEmail()));
        userMapper.updateSelfById(existing);
        return toCurrentResponse(userMapper.selectById(currentUserId));
    }

    @Override
    public void changePassword(Long currentUserId, UserChangePasswordDTO dto) {
        UserEntity existing = ensureExists(currentUserId);
        if (!existing.getUserPwd().equals(md5(dto.getOldPwd()))) {
            throw new IllegalArgumentException("原密码不正确");
        }
        userMapper.updatePasswordById(currentUserId, md5(dto.getNewPwd()));
    }

    @Override
    public UserResponseDTO create(UserCreateDTO dto) {
        validateUnique(null, dto.getUserName(), dto.getUserPhone(), dto.getUserEmail());
        UserEntity entity = new UserEntity();
        entity.setUserName(dto.getUserName());
        entity.setUserPwd(md5(dto.getUserPwd()));
        entity.setUserAvatar(dto.getUserAvatar());
        entity.setUserPhone(emptyToNull(dto.getUserPhone()));
        entity.setUserEmail(emptyToNull(dto.getUserEmail()));
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        userMapper.insert(entity);
        return toResponse(userMapper.selectById(entity.getId()));
    }

    @Override
    public void deleteById(Long id) {
        ensureExists(id);
        userMapper.deleteById(id);
    }

    @Override
    public UserResponseDTO update(Long id, UserUpdateDTO dto) {
        UserEntity existing = ensureExists(id);
        validateUnique(id, dto.getUserName(), dto.getUserPhone(), dto.getUserEmail());
        existing.setUserName(dto.getUserName());
        existing.setUserAvatar(dto.getUserAvatar());
        existing.setUserPhone(emptyToNull(dto.getUserPhone()));
        existing.setUserEmail(emptyToNull(dto.getUserEmail()));
        existing.setStatus(dto.getStatus() == null ? existing.getStatus() : dto.getStatus());
        userMapper.updateById(existing);
        return toResponse(userMapper.selectById(id));
    }

    @Override
    public UserResponseDTO getById(Long id) {
        return toResponse(ensureExists(id));
    }

    @Override
    public PageResult<UserResponseDTO> list(int page, int size, String userName) {
        int offset = (page - 1) * size;
        long total = userMapper.countAll(userName);
        List<UserResponseDTO> list = userMapper.selectPage(offset, size, userName)
                .stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(total, list);
    }

    private UserEntity ensureExists(Long id) {
        UserEntity entity = userMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("记录不存在: id=" + id);
        }
        return entity;
    }

    private Long validateToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new UnauthorizedException("未登录或登录状态已失效");
        }

        Long userId = loginSessionManager.getUserId(token);
        if (userId == null) {
            throw new UnauthorizedException("未登录或登录状态已失效");
        }
        return userId;
    }

    private void validateUnique(Long id, String userName, String userPhone, String userEmail) {
        UserEntity userByName = userMapper.selectByUserName(userName);
        if (userByName != null && !userByName.getId().equals(id)) {
            throw new IllegalArgumentException("用户名已存在");
        }

        String normalizedPhone = emptyToNull(userPhone);
        if (normalizedPhone != null) {
            UserEntity userByPhone = userMapper.selectByUserPhone(normalizedPhone);
            if (userByPhone != null && !userByPhone.getId().equals(id)) {
                throw new IllegalArgumentException("手机号已存在");
            }
        }

        String normalizedEmail = emptyToNull(userEmail);
        if (normalizedEmail != null) {
            UserEntity userByEmail = userMapper.selectByUserEmail(normalizedEmail);
            if (userByEmail != null && !userByEmail.getId().equals(id)) {
                throw new IllegalArgumentException("邮箱已存在");
            }
        }
    }

    private UserResponseDTO toResponse(UserEntity entity) {
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(entity.getId());
        dto.setUserName(entity.getUserName());
        dto.setUserAvatar(entity.getUserAvatar());
        dto.setUserPhone(entity.getUserPhone());
        dto.setUserEmail(entity.getUserEmail());
        dto.setStatus(entity.getStatus());
        dto.setCreateAt(entity.getCreateAt());
        dto.setUpdateAt(entity.getUpdateAt());
        dto.setLastLoginTime(entity.getLastLoginTime());
        return dto;
    }

    private UserCurrentDTO toCurrentResponse(UserEntity entity) {
        UserCurrentDTO dto = new UserCurrentDTO();
        dto.setId(entity.getId());
        dto.setUserName(entity.getUserName());
        dto.setUserAvatar(entity.getUserAvatar());
        dto.setUserPhone(entity.getUserPhone());
        dto.setUserEmail(entity.getUserEmail());
        dto.setStatus(entity.getStatus());
        dto.setCreateAt(entity.getCreateAt());
        dto.setUpdateAt(entity.getUpdateAt());
        dto.setLastLoginTime(entity.getLastLoginTime());
        return dto;
    }

    private String md5(String value) {
        return DigestUtils.md5DigestAsHex(value.getBytes(StandardCharsets.UTF_8));
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
