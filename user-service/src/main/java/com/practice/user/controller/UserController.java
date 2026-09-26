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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

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

    @GetMapping("/me")
    public ApiResult<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResult.success("User found.", users.me(jwt));
    }

    @GetMapping("/{id}")
    public ApiResult<UserResponse> get(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt,
                                        Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return ApiResult.success("User found.", users.getOwned(id, jwt.getSubject(), admin));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResult<List<UserResponse>> list() {
        return ApiResult.success("Users loaded successfully.", users.list());
    }
}
