package com.xym.antifraud.service;

import com.xym.antifraud.exception.BusinessException;
import com.xym.antifraud.mapper.UserSessionMapper;
import com.xym.antifraud.mapper.UserMapper;
import com.xym.antifraud.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserSessionServiceTest {

    @Test
    void resolvesUserFromBearerToken() {
        UserSessionMapper mapper = mock(UserSessionMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        when(mapper.selectUserIdByToken("valid-token")).thenReturn(7L);

        Long userId = new UserSessionService(mapper, userMapper)
                .requireUserId("Bearer valid-token");

        assertThat(userId).isEqualTo(7L);
    }

    @Test
    void rejectsMissingOrExpiredToken() {
        UserSessionMapper mapper = mock(UserSessionMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        when(mapper.selectUserIdByToken("expired-token")).thenReturn(null);
        UserSessionService service = new UserSessionService(mapper, userMapper);

        assertThatThrownBy(() -> service.requireUserId("Bearer expired-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("重新登录");
        assertThatThrownBy(() -> service.requireUserId(null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void requiresAdministratorRole() {
        UserSessionMapper mapper = mock(UserSessionMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        User admin = new User();
        admin.setId(7L);
        admin.setRole("ADMIN");
        when(mapper.selectUserIdByToken("admin-token")).thenReturn(7L);
        when(userMapper.selectById(7L)).thenReturn(admin);

        User resolved = new UserSessionService(mapper, userMapper)
                .requireAdmin("Bearer admin-token");

        assertThat(resolved.getId()).isEqualTo(7L);
    }
}
