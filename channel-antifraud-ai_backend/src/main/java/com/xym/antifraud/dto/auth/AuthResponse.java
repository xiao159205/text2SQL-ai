package com.xym.antifraud.dto.auth;

import java.util.List;

public record AuthResponse(
        Long id,
        String username,
        String phone,
        String avatarPath,
        String role,
        List<String> dataSources,
        String token
) {
}
