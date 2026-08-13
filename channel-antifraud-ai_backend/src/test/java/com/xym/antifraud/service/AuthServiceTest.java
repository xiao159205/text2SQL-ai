package com.xym.antifraud.service;

import com.xym.antifraud.dto.auth.AuthResponse;
import com.xym.antifraud.dto.auth.LoginRequest;
import com.xym.antifraud.dto.auth.RegisterRequest;
import com.xym.antifraud.entity.User;
import com.xym.antifraud.exception.BusinessException;
import com.xym.antifraud.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UserMapper userMapper;
    private AvatarStorageService storageService;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        storageService = mock(AvatarStorageService.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        authService = new AuthService(userMapper, passwordEncoder, storageService);
    }

    @Test
    void registersWithHashedPasswordAndReturnsFrontendContract() {
        RegisterRequest request = registerRequest();
        when(storageService.store(request.getAvatarFile())).thenReturn(
                new AvatarStorageService.StoredAvatar("src/asserts/avatar.png", "avatar.png"));
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(7L);
            return 1;
        });

        AuthResponse response = authService.register(request);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.username()).isEqualTo("测试用户");
        assertThat(response.avatarPath()).isEqualTo("src/asserts/avatar.png");
        assertThat(response.token()).isNotBlank();
        verify(userMapper).insert(org.mockito.ArgumentMatchers.argThat(user ->
                !"secret123".equals(user.getPassword())
                        && passwordEncoder.matches("secret123", user.getPassword())));
    }

    @Test
    void rejectsDuplicatePhoneBeforeSavingAvatar() {
        RegisterRequest request = registerRequest();
        when(userMapper.countByPhone("13800138000")).thenReturn(1);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该手机号已注册");
        verify(storageService, never()).store(any());
    }

    @Test
    void logsInWithPhoneAndBcryptPassword() {
        User user = new User();
        user.setId(8L);
        user.setUsername("登录用户");
        user.setPhone("13900139000");
        user.setPassword(passwordEncoder.encode("secret123"));
        user.setAvatarPath("src/asserts/login.png");
        when(userMapper.selectByPhone("13900139000")).thenReturn(user);

        AuthResponse response = authService.login(new LoginRequest("13900139000", "secret123"));

        assertThat(response.id()).isEqualTo(8L);
        assertThat(response.token()).isNotBlank();
    }

    @Test
    void rejectsWrongPassword() {
        User user = new User();
        user.setPassword(passwordEncoder.encode("correct-password"));
        when(userMapper.selectByPhone("13900139000")).thenReturn(user);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("13900139000", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("手机号或密码错误");
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(" 测试用户 ");
        request.setPhone("13800138000");
        request.setPassword("secret123");
        request.setAvatarFile(new MockMultipartFile(
                "avatarFile", "avatar.png", "image/png", new byte[]{1, 2, 3}));
        return request;
    }
}
