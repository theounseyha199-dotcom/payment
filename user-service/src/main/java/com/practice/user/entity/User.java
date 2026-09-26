package com.practice.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "app_users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(unique = true)
    private String keycloakSubject;

    @Column(updatable = false)
    private Instant createdAt;

    protected User() { }

    public User(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public User(String name, String email, String keycloakSubject) {
        this(name, email);
        this.keycloakSubject = keycloakSubject;
    }

    @jakarta.persistence.PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getKeycloakSubject() { return keycloakSubject; }
    public Instant getCreatedAt() { return createdAt; }
}
