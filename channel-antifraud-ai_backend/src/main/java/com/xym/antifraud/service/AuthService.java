package com.xym.antifraud.service;

import com.xym.antifraud.dto.auth.AuthResponse;
import com.xym.antifraud.dto.auth.LoginRequest;
import com.xym.antifraud.dto.auth.RegisterRequest;
import com.xym.antifraud.entity.User;
import com.xym.antifraud.exception.BusinessException;
import com.xym.antifraud.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AvatarStorageService avatarStorageService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                       AvatarStorageService avatarStorageService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.avatarStorageService = avatarStorageService;
    }

    public AuthResponse login(LoginRequest request) {
        User user = userMapper.selectByPhone(request.phone().trim());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                    "手机号或密码错误");
        }
        return toResponse(user);
    }

    public AuthResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String phone = request.getPhone().trim();
        if (userMapper.countByPhone(phone) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "PHONE_EXISTS", "该手机号已注册");
        }
        if (userMapper.countByUsername(username) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "该用户名已存在");
        }

        AvatarStorageService.StoredAvatar avatar = avatarStorageService.store(request.getAvatarFile());
        try {
            User user = new User();
            user.setUsername(username);
            user.setPhone(phone);
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setAvatarPath(avatar.publicPath());
            userMapper.insert(user);
            return toResponse(user);
        } catch (DuplicateKeyException exception) {
            avatarStorageService.deleteQuietly(avatar);
            throw new BusinessException(HttpStatus.CONFLICT, "USER_EXISTS", "手机号或用户名已存在");
        } catch (RuntimeException exception) {
            avatarStorageService.deleteQuietly(avatar);
            throw exception;
        }
    }

    private AuthResponse toResponse(User user) {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        return new AuthResponse(user.getId(), user.getUsername(), user.getPhone(),
                user.getAvatarPath(), token);
    }
}
