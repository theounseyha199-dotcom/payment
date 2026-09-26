package com.practice.user.service;

import com.practice.user.dto.CreateUserRequest;
import com.practice.user.dto.UserResponse;
import com.practice.user.dto.AdminUserResponse;
import com.practice.user.dto.PageResponse;
import com.practice.user.dto.UserStatsResponse;
import com.practice.user.entity.User;
import com.practice.user.repository.UserRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {
    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    public UserResponse create(CreateUserRequest request) {
        if (users.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }
        return UserResponse.from(users.save(new User(request.name(), request.email())));
    }

    public UserResponse me(Jwt jwt) {
        return users.findByKeycloakSubject(jwt.getSubject()).map(UserResponse::from)
                .orElseGet(() -> createProfile(jwt));
    }

    private UserResponse createProfile(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (!StringUtils.hasText(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account email is required.");
        }
        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This email belongs to an existing profile. Contact an administrator to migrate it.");
        }
        String name = jwt.getClaimAsString("name");
        if (!StringUtils.hasText(name)) {
            name = email;
        }
        return UserResponse.from(users.save(new User(name, email, jwt.getSubject())));
    }

    public UserResponse getOwned(Long id, String subject, boolean admin) {
        User user = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (!admin && !subject.equals(user.getKeycloakSubject())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied.");
        }
        return UserResponse.from(user);
    }

    public UserResponse get(Long id) {
        User user = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return UserResponse.from(user);
    }

    public List<UserResponse> list() {
        return users.findAll().stream().map(UserResponse::from).toList();
    }

    public PageResponse<AdminUserResponse> adminList(String search, Pageable pageable) {
        Specification<User> specification = searchSpecification(search);
        Page<AdminUserResponse> page = users.findAll(specification, pageable).map(AdminUserResponse::from);
        return PageResponse.from(page);
    }

    public AdminUserResponse adminGet(Long id) {
        return AdminUserResponse.from(users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.")));
    }

    public UserStatsResponse adminStats() {
        long total = users.count();
        long linked = users.countByKeycloakSubjectIsNotNull();
        return new UserStatsResponse(total, linked, total - linked);
    }

    private Specification<User> searchSpecification(String search) {
        if (!StringUtils.hasText(search)) {
            return null;
        }
        String value = search.trim().toLowerCase();
        return (root, query, builder) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(builder.like(builder.lower(root.get("name")), "%" + value + "%"));
            predicates.add(builder.like(builder.lower(root.get("email")), "%" + value + "%"));
            predicates.add(builder.like(builder.lower(root.get("keycloakSubject")), "%" + value + "%"));
            try {
                predicates.add(builder.equal(root.get("id"), Long.valueOf(value)));
            } catch (NumberFormatException ignored) {
                // A non-numeric search cannot match an application ID.
            }
            return builder.or(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
