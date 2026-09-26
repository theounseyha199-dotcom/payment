package com.practice.user.dto;

public record UserStatsResponse(long totalUsers, long linkedToKeycloak, long legacyProfiles) { }
