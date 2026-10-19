package com.xym.antifraud.service;

import com.xym.antifraud.exception.BusinessException;
import com.xym.antifraud.entity.User;
import com.xym.antifraud.mapper.UserMapper;
import com.xym.antifraud.mapper.UserSessionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;

@Service
public class UserSessionService {

    private static final int SESSION_DAYS = 7;
    private final SecureRandom secureRandom = new SecureRandom();
    private final UserSessionMapper mapper;
    private final UserMapper userMapper;

    public UserSessionService(UserSessionMapper mapper, UserMapper userMapper) {
        this.mapper = mapper;
        this.userMapper = userMapper;
    }

    public String create(Long userId) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        mapper.insert(token, userId, LocalDateTime.now().plusDays(SESSION_DAYS));
        return token;
    }

    public Long requireUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw unauthorized();
        }
        String token = authorization.substring(7).trim();
        Long userId = token.isEmpty() ? null : mapper.selectUserIdByToken(token);
        if (userId == null) {
            throw unauthorized();
        }
        return userId;
    }

    public User requireAdmin(String authorization) {
        Long userId = requireUserId(authorization);
        User user = userMapper.selectById(userId);
        if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED",
                    "仅管理员可以执行此操作");
        }
        return user;
    }

    public Long requireDataSourceAccess(String authorization, String dataSource) {
        Long userId = requireUserId(authorization);
        User user = userMapper.selectById(userId);
        if (user != null && "ADMIN".equalsIgnoreCase(user.getRole())) return userId;
        String normalized = com.xym.antifraud.datasource.DatabaseType.from(dataSource)
                .name().toLowerCase(Locale.ROOT);
        if (!userMapper.selectDataSources(userId).contains(normalized)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "DATA_SOURCE_FORBIDDEN",
                    "你没有该数据源的访问权限，请联系管理员");
        }
        return userId;
    }

    private BusinessException unauthorized() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "登录状态已失效，请重新登录");
    }
}
