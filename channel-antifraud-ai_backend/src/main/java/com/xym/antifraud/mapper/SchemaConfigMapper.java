package com.xym.antifraud.mapper;

import com.xym.antifraud.entity.SchemaFieldRule;
import com.xym.antifraud.entity.SchemaTableRule;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 黑白名单配置的 MyBatis 读写接口。 */
public interface SchemaConfigMapper {

    int upsertTableRule(@Param("databaseType") String databaseType,
                        @Param("tableName") String tableName,
                        @Param("visible") int visible,
                        @Param("customComment") String customComment);

    int upsertFieldRule(@Param("databaseType") String databaseType,
                        @Param("tableName") String tableName,
                        @Param("fieldName") String fieldName,
                        @Param("visible") int visible,
                        @Param("customComment") String customComment);

    List<SchemaTableRule> selectTableRules(@Param("databaseType") String databaseType);

    List<SchemaFieldRule> selectFieldRules(@Param("databaseType") String databaseType);
}
