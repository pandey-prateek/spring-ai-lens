package com.bluntyrod.springailens.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.bluntyrod.springailens.util.EventStore;
import com.bluntyrod.springailens.util.store.InMemoryEventStore;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "ai-lens.storage", name = "type", havingValue = "MEMORY", matchIfMissing = true)
public class AiLensMemoryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EventStore.class)
    public EventStore aiLensEventStore(AiLensProperties properties) {
        return new InMemoryEventStore(properties.getBufferSize());
    }
}
