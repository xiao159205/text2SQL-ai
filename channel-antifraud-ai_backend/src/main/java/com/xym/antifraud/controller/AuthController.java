package com.xym.antifraud.controller;

import com.xym.antifraud.dto.auth.AuthResponse;
import com.xym.antifraud.dto.auth.LoginRequest;
import com.xym.antifraud.dto.auth.RegisterRequest;
import com.xym.antifraud.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AuthResponse register(@Valid @ModelAttribute RegisterRequest request) {
        return authService.register(request);
    }
}
