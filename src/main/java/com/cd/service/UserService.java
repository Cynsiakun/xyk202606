package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.UserChangePasswordDTO;
import com.cd.dto.UserCreateDTO;
import com.cd.dto.UserAvatarUploadResponseDTO;
import com.cd.dto.UserCurrentDTO;
import com.cd.dto.UserLoginDTO;
import com.cd.dto.UserLoginResponseDTO;
import com.cd.dto.UserResponseDTO;
import com.cd.dto.UserUpdateDTO;
import com.cd.dto.UserUpdateSelfDTO;
import org.springframework.web.multipart.MultipartFile;

public interface UserService {

    UserLoginResponseDTO login(UserLoginDTO dto, String ipAddress);

    UserCurrentDTO currentUser();

    UserCurrentDTO currentUser(Long currentUserId);

    void logout();

    UserCurrentDTO updateSelf(UserUpdateSelfDTO dto);

    UserCurrentDTO updateSelf(Long currentUserId, UserUpdateSelfDTO dto);

    UserAvatarUploadResponseDTO uploadAvatar(MultipartFile file);

    UserAvatarUploadResponseDTO uploadAvatar(Long currentUserId, MultipartFile file);

    void changePassword(UserChangePasswordDTO dto);

    void changePassword(Long currentUserId, UserChangePasswordDTO dto);

    UserResponseDTO create(UserCreateDTO dto);

    void deleteById(Long id);

    UserResponseDTO update(Long id, UserUpdateDTO dto);

    UserResponseDTO getById(Long id);

    PageResult<UserResponseDTO> list(int page, int size, String userName);
}
