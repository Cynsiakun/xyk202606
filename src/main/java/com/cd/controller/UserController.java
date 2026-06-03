package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.dto.UserAvatarUploadResponseDTO;
import com.cd.dto.UserChangePasswordDTO;
import com.cd.dto.UserCreateDTO;
import com.cd.dto.UserCurrentDTO;
import com.cd.dto.UserLoginDTO;
import com.cd.dto.UserLoginResponseDTO;
import com.cd.dto.UserResponseDTO;
import com.cd.dto.UserUpdateDTO;
import com.cd.dto.UserUpdateSelfDTO;
import com.cd.service.UserService;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
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
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PermitAll
    @PostMapping("/login")
    public Result<UserLoginResponseDTO> login(@Valid @RequestBody UserLoginDTO dto, HttpServletRequest request) {
        return Result.success(userService.login(dto, request.getRemoteAddr()));
    }

    @GetMapping("/current")
    public Result<UserCurrentDTO> currentUser() {
        return Result.success(userService.currentUser());
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        userService.logout();
        return Result.success();
    }

    @PutMapping("/updateSelf")
    public Result<UserCurrentDTO> updateSelf(@Valid @RequestBody UserUpdateSelfDTO dto) {
        return Result.success(userService.updateSelf(dto));
    }

    @PostMapping(value = "/avatar/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<UserAvatarUploadResponseDTO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return new Result<>(200, "上传成功", userService.uploadAvatar(file));
    }

    @PostMapping("/changePassword")
    public Result<Void> changePassword(@Valid @RequestBody UserChangePasswordDTO dto) {
        userService.changePassword(dto);
        return Result.success();
    }

    @PreAuthorize("hasAuthority('user:create')")
    @PostMapping
    public Result<UserResponseDTO> create(@Valid @RequestBody UserCreateDTO dto) {
        return Result.success(userService.create(dto));
    }

    @PreAuthorize("hasAuthority('user:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(value = 1, message = "id must be greater than 0") Long id) {
        userService.deleteById(id);
        return Result.success();
    }

    @PreAuthorize("hasAuthority('user:update')")
    @PutMapping("/{id}")
    public Result<UserResponseDTO> update(@PathVariable @Min(value = 1, message = "id must be greater than 0") Long id,
                                          @Valid @RequestBody UserUpdateDTO dto) {
        return Result.success(userService.update(id, dto));
    }

    @PreAuthorize("hasAuthority('user:view')")
    @GetMapping("/{id}")
    public Result<UserResponseDTO> getById(@PathVariable @Min(value = 1, message = "id must be greater than 0") Long id) {
        return Result.success(userService.getById(id));
    }

    @PreAuthorize("hasAuthority('user:view')")
    @GetMapping("/list")
    public Result<PageResult<UserResponseDTO>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page must be greater than 0") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size must be greater than 0") Integer size,
            @RequestParam(required = false) String userName) {
        return Result.success(userService.list(page, size, userName));
    }
}
