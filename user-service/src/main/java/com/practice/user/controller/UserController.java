package com.practice.user.controller;

import com.practice.user.dto.CreateUserRequest;
import com.practice.user.dto.ApiResult;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.practice.user.dto.UserResponse;
import com.practice.user.service.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @PostMapping
    @ApiResponse(responseCode = "201", description = "User created")
    public ResponseEntity<ApiResult<UserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = users.create(request);
        return ResponseEntity.created(URI.create("/users/" + user.id()))
                .body(ApiResult.success("User created successfully.", user));
    }

    @GetMapping("/{id}")
    public ApiResult<UserResponse> get(@PathVariable Long id) {
        return ApiResult.success("User found.", users.get(id));
    }

    @GetMapping
    public ApiResult<List<UserResponse>> list() {
        return ApiResult.success("Users loaded successfully.", users.list());
    }
}
