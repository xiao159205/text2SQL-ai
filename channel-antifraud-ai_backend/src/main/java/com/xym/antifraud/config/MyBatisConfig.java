package com.xym.antifraud.config;

import com.xym.antifraud.mapper.SchemaConfigMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/** 黑白名单 Mapper 固定使用可写的 MySQL 配置数据源。 */
@Configuration
@MapperScan(
        basePackageClasses = SchemaConfigMapper.class,
        sqlSessionFactoryRef = "configSqlSessionFactory")
public class MyBatisConfig {

    @Bean(name = "configSqlSessionFactory")
    public SqlSessionFactory configSqlSessionFactory(
            @Qualifier("configDataSource") DataSource configDataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(configDataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/*.xml"));
        org.apache.ibatis.session.Configuration configuration =
                new org.apache.ibatis.session.Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);
        return factory.getObject();
    }
}
