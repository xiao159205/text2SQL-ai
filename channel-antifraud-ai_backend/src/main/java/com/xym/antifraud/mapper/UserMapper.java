package com.xym.antifraud.mapper;

import com.xym.antifraud.entity.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper {

    User selectByAccount(@Param("account") String account);

    User selectById(@Param("id") Long id);

    List<User> selectAll(@Param("keyword") String keyword);

    int countByPhone(@Param("phone") String phone);

    int countByUsername(@Param("username") String username);

    int countByPhoneExcludingId(@Param("phone") String phone, @Param("id") Long id);

    int countByUsernameExcludingId(@Param("username") String username, @Param("id") Long id);

    int countAll();

    int countAdmins();

    int insert(User user);

    int update(User user);

    int deleteById(@Param("id") Long id);

    List<String> selectDataSources(@Param("userId") Long userId);

    int deleteDataSources(@Param("userId") Long userId);

    int insertDataSources(@Param("userId") Long userId,
                          @Param("dataSources") List<String> dataSources);
}
