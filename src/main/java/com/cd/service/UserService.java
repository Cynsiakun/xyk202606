package com.cd.service;

import com.cd.common.PageResult;
import com.cd.dto.UserChangePasswordDTO;
import com.cd.dto.UserCreateDTO;
import com.cd.dto.UserCurrentDTO;
import com.cd.dto.UserLoginDTO;
import com.cd.dto.UserLoginResponseDTO;
import com.cd.dto.UserResponseDTO;
import com.cd.dto.UserUpdateDTO;
import com.cd.dto.UserUpdateSelfDTO;

public interface UserService {

    UserLoginResponseDTO login(UserLoginDTO dto, String ipAddress);

    UserCurrentDTO currentUser(Long currentUserId);

    void logout(String token);

    UserCurrentDTO updateSelf(Long currentUserId, UserUpdateSelfDTO dto);

    void changePassword(Long currentUserId, UserChangePasswordDTO dto);

    UserResponseDTO create(UserCreateDTO dto);

    void deleteById(Long id);

    UserResponseDTO update(Long id, UserUpdateDTO dto);

    UserResponseDTO getById(Long id);

    PageResult<UserResponseDTO> list(int page, int size, String userName);
}
