package com.cd.controller;

import com.cd.common.Result;
import com.cd.dto.UserCurrentDTO;
import com.cd.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CurrentUserController {

    private final UserService userService;

    @GetMapping("/api/current-user")
    public Result<UserCurrentDTO> currentUser() {
        return Result.success(userService.currentUser());
    }
}
