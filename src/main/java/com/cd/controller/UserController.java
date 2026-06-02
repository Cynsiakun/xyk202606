package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.common.constant.AuthConstants;
import com.cd.dto.UserChangePasswordDTO;
import com.cd.dto.UserCreateDTO;
import com.cd.dto.UserCurrentDTO;
import com.cd.dto.UserLoginDTO;
import com.cd.dto.UserLoginResponseDTO;
import com.cd.dto.UserResponseDTO;
import com.cd.dto.UserUpdateDTO;
import com.cd.dto.UserUpdateSelfDTO;
import com.cd.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/login")
    public Result<UserLoginResponseDTO> login(@Valid @RequestBody UserLoginDTO dto, HttpServletRequest request) {
        return Result.success(userService.login(dto, request.getRemoteAddr()));
    }

    @GetMapping("/current")
    public Result<UserCurrentDTO> currentUser(@RequestAttribute(AuthConstants.CURRENT_USER_ID) Long currentUserId) {
        return Result.success(userService.currentUser(currentUserId));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestAttribute(AuthConstants.CURRENT_TOKEN) String currentToken) {
        userService.logout(currentToken);
        return Result.success();
    }

    @PutMapping("/updateSelf")
    public Result<UserCurrentDTO> updateSelf(@RequestAttribute(AuthConstants.CURRENT_USER_ID) Long currentUserId,
                                             @Valid @RequestBody UserUpdateSelfDTO dto) {
        return Result.success(userService.updateSelf(currentUserId, dto));
    }

    @PostMapping("/changePassword")
    public Result<Void> changePassword(@RequestAttribute(AuthConstants.CURRENT_USER_ID) Long currentUserId,
                                       @Valid @RequestBody UserChangePasswordDTO dto) {
        userService.changePassword(currentUserId, dto);
        return Result.success();
    }

    @PostMapping
    public Result<UserResponseDTO> create(@Valid @RequestBody UserCreateDTO dto) {
        return Result.success(userService.create(dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(value = 1, message = "id must be greater than 0") Long id) {
        userService.deleteById(id);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<UserResponseDTO> update(@PathVariable @Min(value = 1, message = "id must be greater than 0") Long id,
                                          @Valid @RequestBody UserUpdateDTO dto) {
        return Result.success(userService.update(id, dto));
    }

    @GetMapping("/{id}")
    public Result<UserResponseDTO> getById(@PathVariable @Min(value = 1, message = "id must be greater than 0") Long id) {
        return Result.success(userService.getById(id));
    }

    @GetMapping("/list")
    public Result<PageResult<UserResponseDTO>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be greater than 0") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size must be greater than 0") Integer size,
            @RequestParam(required = false) String userName) {
        return Result.success(userService.list(page, size, userName));
    }
}
