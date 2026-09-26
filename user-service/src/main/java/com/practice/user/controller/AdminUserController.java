package com.practice.user.controller;

import com.practice.user.dto.ApiResult;
import com.practice.user.dto.AdminUserResponse;
import com.practice.user.dto.PageResponse;
import com.practice.user.dto.UserStatsResponse;
import com.practice.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Users", description = "Administrator user profile and statistics APIs")
public class AdminUserController {
    private final UserService users;

    public AdminUserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    @Operation(summary = "List application users for administrators")
    public ApiResult<PageResponse<AdminUserResponse>> list(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String search,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "createdAt,desc") String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be 1-100.");
        }
        return ApiResult.success("Users loaded successfully.", users.adminList(search, pageable(page, size, sort)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an application user for administrators")
    public ApiResult<AdminUserResponse> get(@PathVariable Long id) {
        return ApiResult.success("User found.", users.adminGet(id));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get application user statistics")
    public ApiResult<UserStatsResponse> stats() {
        return ApiResult.success("User statistics retrieved.", users.adminStats());
    }

    private Pageable pageable(int page, int size, String sort) {
        String[] parts = sort.split(",", -1);
        String property = parts[0];
        if (!java.util.Set.of("id", "name", "email", "createdAt", "keycloakSubject").contains(property)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort field.");
        }
        Sort.Direction direction = parts.length > 1 ? Sort.Direction.fromOptionalString(parts[1]).orElse(null) : Sort.Direction.DESC;
        if (direction == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sort direction must be asc or desc.");
        }
        return PageRequest.of(page, size, Sort.by(direction, property));
    }
}
