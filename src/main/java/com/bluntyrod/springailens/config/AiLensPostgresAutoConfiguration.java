package com.bluntyrod.springailens.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.bluntyrod.springailens.util.EventStore;
import com.bluntyrod.springailens.util.store.PostgresEventStore;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ai-lens.storage", name = "type", havingValue = "POSTGRES")
@ConditionalOnClass(JdbcTemplate.class)
public class AiLensPostgresAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EventStore.class)
    public EventStore aiLensEventStore(JdbcTemplate jdbcTemplate,
                                       AiLensProperties properties) {
        return new PostgresEventStore(jdbcTemplate, properties);
    }
}
