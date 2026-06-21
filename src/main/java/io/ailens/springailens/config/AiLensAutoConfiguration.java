package io.ailens.springailens.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import io.ailens.springailens.actuator.AiLensEndpoint;
import io.ailens.springailens.util.anomaly.AnomalyDetector;
import io.ailens.springailens.util.diff.PromptDiffTracker;
import io.ailens.springailens.util.interceptor.AiLensInterceptor;
import io.ailens.springailens.util.store.RingBufferEventStore;
import io.ailens.springailens.web.AiLensDashboardController;

@AutoConfiguration
public class AiLensAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RingBufferEventStore aiLensEventStore() {
        return new RingBufferEventStore(500);
    }

    @Bean
    @ConditionalOnMissingBean
    public AnomalyDetector aiLensAnomalyDetector(RingBufferEventStore store) {
        return new AnomalyDetector(store);
    }

    @Bean
    @ConditionalOnMissingBean
    public PromptDiffTracker aiLensPromptDiffTracker() {
        return new PromptDiffTracker();
    }

    @Bean
    @ConditionalOnMissingBean
    public AiLensInterceptor aiLensInterceptor(RingBufferEventStore store,
                                               AnomalyDetector detector,
                                               PromptDiffTracker diffTracker) {
        return new AiLensInterceptor(store, detector, diffTracker);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiLensEndpoint aiLensEndpoint(RingBufferEventStore store) {
        return new AiLensEndpoint(store);
    }

    @Bean
    @ConditionalOnMissingBean
    public AiLensDashboardController aiLensDashboardController(RingBufferEventStore store) {
        return new AiLensDashboardController(store);
    }
}
