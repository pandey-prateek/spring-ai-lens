package io.ailens.springailens.config;

import io.ailens.springailens.interceptor.AiLensInterceptor;
import io.ailens.springailens.store.RingBufferEventStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class AiLensAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RingBufferEventStore aiLensEventStore() {
        return new RingBufferEventStore(500);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiLensInterceptor aiLensInterceptor(RingBufferEventStore store) {
        return new AiLensInterceptor(store);
    }
}