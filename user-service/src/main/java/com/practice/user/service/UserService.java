package com.practice.user.service;

import com.practice.user.dto.CreateUserRequest;
import com.practice.user.dto.UserResponse;
import com.practice.user.entity.User;
import com.practice.user.repository.UserRepository;
import java.util.List;
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

    public UserResponse get(Long id) {
        User user = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return UserResponse.from(user);
    }

    public List<UserResponse> list() {
        return users.findAll().stream().map(UserResponse::from).toList();
    }
}
