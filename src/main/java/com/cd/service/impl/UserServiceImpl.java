package com.cd.service.impl;

import com.cd.common.PageResult;
import com.cd.common.config.CacheConfig;
import com.cd.common.exception.ResourceNotFoundException;
import com.cd.common.exception.UnauthorizedException;
import com.cd.common.security.CustomUserDetailsService;
import com.cd.common.security.JwtTokenBlacklistService;
import com.cd.common.security.JwtTokenProvider;
import com.cd.common.security.Md5PasswordEncoder;
import com.cd.common.security.SecurityUser;
import com.cd.common.security.SecurityUtils;
import com.cd.dto.UserAvatarUploadResponseDTO;
import com.cd.dto.UserChangePasswordDTO;
import com.cd.dto.UserCreateDTO;
import com.cd.dto.UserCurrentDTO;
import com.cd.dto.UserLoginDTO;
import com.cd.dto.UserLoginResponseDTO;
import com.cd.dto.UserResponseDTO;
import com.cd.dto.UserUpdateDTO;
import com.cd.dto.UserUpdateSelfDTO;
import com.cd.entity.UserEntity;
import com.cd.mapper.RbacMapper;
import com.cd.mapper.UserMapper;
import com.cd.service.LoginLogService;
import com.cd.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Set<String> ALLOWED_AVATAR_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif");
    private static final long MAX_AVATAR_SIZE = 5L * 1024 * 1024;

    private final UserMapper userMapper;
    private final RbacMapper rbacMapper;
    private final LoginLogService loginLogService;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService customUserDetailsService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtTokenBlacklistService jwtTokenBlacklistService;
    private final Md5PasswordEncoder md5PasswordEncoder;

    @Override
    public UserLoginResponseDTO login(UserLoginDTO dto, String ipAddress) {
        UserEntity user = userMapper.selectByUserName(dto.getUserName());
        if (user == null) {
            loginLogService.record(null, dto.getUserName(), ipAddress, 0, "用户名或密码错误");
            throw new UnauthorizedException("用户名或密码错误");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.getUserName(), dto.getPassword())
            );
            SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
            List<String> roles = customUserDetailsService.loadRoleCodes(securityUser.getUserId());
            String token = jwtTokenProvider.createToken(securityUser, roles);

            userMapper.updateLastLoginTime(user.getId());
            UserEntity latestUser = userMapper.selectById(user.getId());
            loginLogService.record(latestUser.getId(), latestUser.getUserName(), ipAddress, 1, "登录成功");

            UserLoginResponseDTO response = new UserLoginResponseDTO();
            response.setToken(token);
            response.setUserId(latestUser.getId());
            response.setUserName(latestUser.getUserName());
            response.setLastLoginTime(latestUser.getLastLoginTime());
            return response;
        } catch (BadCredentialsException e) {
            loginLogService.record(user.getId(), dto.getUserName(), ipAddress, 0, "用户名或密码错误");
            throw new UnauthorizedException("用户名或密码错误");
        }
    }

    @Override
    public UserCurrentDTO currentUser() {
        Long currentUserId = requireCurrentUserId();
        return currentUser(currentUserId);
    }

    @Override
    public UserCurrentDTO currentUser(Long currentUserId) {
        return toCurrentResponse(ensureExists(currentUserId));
    }

    @Override
    public void logout() {
        String token = SecurityUtils.getCurrentToken();
        validateToken(token);
        jwtTokenBlacklistService.revokeToken(token, jwtTokenProvider.parseClaims(token));
    }

    @Override
    public UserCurrentDTO updateSelf(UserUpdateSelfDTO dto) {
        return updateSelf(requireCurrentUserId(), dto);
    }

    @Override
    public UserCurrentDTO updateSelf(Long currentUserId, UserUpdateSelfDTO dto) {
        UserEntity existing = ensureExists(currentUserId);
        validateUnique(currentUserId, existing.getUserName(), dto.getUserPhone(), dto.getUserEmail());
        if (dto.getUserAvatar() != null) {
            existing.setUserAvatar(dto.getUserAvatar());
        }
        existing.setUserPhone(emptyToNull(dto.getUserPhone()));
        existing.setUserEmail(emptyToNull(dto.getUserEmail()));
        userMapper.updateSelfById(existing);
        return toCurrentResponse(userMapper.selectById(currentUserId));
    }

    @Override
    public UserAvatarUploadResponseDTO uploadAvatar(MultipartFile file) {
        return uploadAvatar(requireCurrentUserId(), file);
    }

    @Override
    public UserAvatarUploadResponseDTO uploadAvatar(Long currentUserId, MultipartFile file) {
        UserEntity existing = ensureExists(currentUserId);
        validateAvatarFile(file);

        String extension = getFileExtension(file.getOriginalFilename());
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path avatarDirectory = Paths.get("uploads", "avatar").toAbsolutePath().normalize();
        Path targetPath = avatarDirectory.resolve(fileName);

        try {
            Files.createDirectories(avatarDirectory);
            file.transferTo(targetPath);
        } catch (IOException e) {
            throw new IllegalArgumentException("头像上传失败");
        }

        String avatarUrl = "/uploads/avatar/" + fileName;
        existing.setUserAvatar(avatarUrl);
        userMapper.updateSelfById(existing);

        UserAvatarUploadResponseDTO response = new UserAvatarUploadResponseDTO();
        response.setAvatarUrl(avatarUrl);
        return response;
    }

    @Override
    public void changePassword(UserChangePasswordDTO dto) {
        changePassword(requireCurrentUserId(), dto);
    }

    @Override
    public void changePassword(Long currentUserId, UserChangePasswordDTO dto) {
        UserEntity existing = ensureExists(currentUserId);
        if (!md5PasswordEncoder.matches(dto.getOldPwd(), existing.getUserPwd())) {
            throw new IllegalArgumentException("原密码不正确");
        }
        if (dto.getNewPwd().equals(dto.getOldPwd())) {
            throw new IllegalArgumentException("新密码不能与当前密码相同");
        }
        if (!dto.getNewPwd().matches("^(?=.*[A-Za-z])(?=.*\\d).{8,64}$")) {
            throw new IllegalArgumentException("新密码长度不少于8位，且必须同时包含字母和数字");
        }
        if (!dto.getNewPwd().equals(dto.getConfirmPwd())) {
            throw new IllegalArgumentException("两次输入的新密码必须一致");
        }
        userMapper.updatePasswordById(currentUserId, md5PasswordEncoder.encode(dto.getNewPwd()));

        String token = SecurityUtils.getCurrentToken();
        if (StringUtils.hasText(token)) {
            validateToken(token);
            jwtTokenBlacklistService.revokeToken(token, jwtTokenProvider.parseClaims(token));
        }
    }

    @Override
    public UserResponseDTO create(UserCreateDTO dto) {
        validateUnique(null, dto.getUserName(), dto.getUserPhone(), dto.getUserEmail());
        UserEntity entity = new UserEntity();
        entity.setUserName(dto.getUserName());
        entity.setUserPwd(md5PasswordEncoder.encode(dto.getUserPwd()));
        entity.setUserAvatar(dto.getUserAvatar());
        entity.setUserPhone(emptyToNull(dto.getUserPhone()));
        entity.setUserEmail(emptyToNull(dto.getUserEmail()));
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        userMapper.insert(entity);
        return toResponse(userMapper.selectById(entity.getId()));
    }

    @Override
    @CacheEvict(value = CacheConfig.USER_AUTH_CACHE, key = "#id")
    public void deleteById(Long id) {
        ensureExists(id);
        userMapper.deleteById(id);
    }

    @Override
    @CacheEvict(value = CacheConfig.USER_AUTH_CACHE, key = "#id")
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
        if (!StringUtils.hasText(token) || !jwtTokenProvider.isValid(token) || jwtTokenBlacklistService.isRevoked(token)) {
            throw new UnauthorizedException("未登录或登录状态已失效");
        }
        return jwtTokenProvider.getUserId(token);
    }

    private Long requireCurrentUserId() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            throw new UnauthorizedException("未登录或登录状态已失效");
        }
        return currentUserId;
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
        dto.setRoles(rbacMapper.selectRoleNamesByUserId(entity.getId()));
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

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private void validateAvatarFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择图片文件");
        }

        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new IllegalArgumentException("图片大小不能超过5MB");
        }

        String extension = getFileExtension(file.getOriginalFilename());
        if (!ALLOWED_AVATAR_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持 jpg、jpeg、png、gif 格式图片");
        }

        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("仅支持上传图片文件");
        }

        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                throw new IllegalArgumentException("仅支持上传图片文件");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("读取图片文件失败");
        }
    }

    private String getFileExtension(String originalFilename) {
        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (!StringUtils.hasText(extension)) {
            throw new IllegalArgumentException("文件格式不正确");
        }
        return extension.toLowerCase(Locale.ROOT);
    }
}
