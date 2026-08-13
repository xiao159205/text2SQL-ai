package com.xym.antifraud.mapper;

import com.xym.antifraud.entity.User;
import org.apache.ibatis.annotations.Param;

public interface UserMapper {

    User selectByPhone(@Param("phone") String phone);

    int countByPhone(@Param("phone") String phone);

    int countByUsername(@Param("username") String username);

    int insert(User user);
}
