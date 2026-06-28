package com.bluntyrod.springailens.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.bluntyrod.springailens.util.EventStore;
import com.bluntyrod.springailens.util.store.RedisEventStore;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ai-lens.storage", name = "type", havingValue = "REDIS")
@ConditionalOnClass(StringRedisTemplate.class)
public class AiLensRedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EventStore.class)
    public EventStore aiLensEventStore(StringRedisTemplate redisTemplate,
                                       AiLensProperties properties) {
        return new RedisEventStore(redisTemplate, properties);
    }
}
