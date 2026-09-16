package com.cd.controller;

import com.cd.common.Result;
import com.cd.common.access.AccessEffectiveDTO;
import com.cd.common.access.AccessPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/access")
@RequiredArgsConstructor
public class AccessController {

    private final AccessPolicyService accessPolicyService;

    @GetMapping("/effective")
    public Result<AccessEffectiveDTO> effective() {
        return Result.success(accessPolicyService.currentEffective());
    }
}
