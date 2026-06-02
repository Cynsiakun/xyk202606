package com.cd.controller;

import com.cd.common.PageResult;
import com.cd.common.Result;
import com.cd.dto.TestCreateDTO;
import com.cd.dto.TestResponseDTO;
import com.cd.dto.TestUpdateDTO;
import com.cd.service.TestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {

    private final TestService testService;

    @PostMapping
    public Result<TestResponseDTO> create(@Valid @RequestBody TestCreateDTO dto) {
        return Result.success(testService.create(dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Min(value = 1, message = "id必须大于0") Integer id) {
        testService.deleteById(id);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<TestResponseDTO> update(@PathVariable @Min(value = 1, message = "id必须大于0") Integer id,
                                          @Valid @RequestBody TestUpdateDTO dto) {
        return Result.success(testService.update(id, dto));
    }

    @GetMapping("/{id}")
    public Result<TestResponseDTO> getById(@PathVariable @Min(value = 1, message = "id必须大于0") Integer id) {
        return Result.success(testService.getById(id));
    }

    @GetMapping("/list")
    public Result<PageResult<TestResponseDTO>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "page必须大于0") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "size必须大于0") Integer size) {
        return Result.success(testService.list(page, size));
    }
}
