package com.xym.antifraud.dto.admin;

import java.time.LocalDateTime;
import java.util.List;

public record AdminUserResponse(
        Long id,
        String username,
        String phone,
        String avatarPath,
        String role,
        List<String> dataSources,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
