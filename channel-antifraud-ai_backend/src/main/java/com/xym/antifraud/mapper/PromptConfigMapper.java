package com.xym.antifraud.mapper;

import com.xym.antifraud.entity.PromptConfig;
import org.apache.ibatis.annotations.Param;

/** Text2SQL System Prompt 配置的 MyBatis 读写接口。 */
public interface PromptConfigMapper {

    PromptConfig selectByDatabaseType(@Param("databaseType") String databaseType);

    int upsert(@Param("databaseType") String databaseType,
               @Param("content") String content);

    int deleteByDatabaseType(@Param("databaseType") String databaseType);
}
