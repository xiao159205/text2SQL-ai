package com.xym.antifraud.dto.auth;

public record AuthResponse(
        Long id,
        String username,
        String phone,
        String avatarPath,
        String token
) {
}
