package com.xym.antifraud.mapper;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

public interface UserSessionMapper {
    int insert(@Param("token") String token, @Param("userId") Long userId,
               @Param("expiresAt") LocalDateTime expiresAt);
    Long selectUserIdByToken(@Param("token") String token);
    int deleteByToken(@Param("token") String token);
}
