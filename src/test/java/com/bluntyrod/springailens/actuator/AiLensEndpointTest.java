package com.bluntyrod.springailens.actuator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;

import com.bluntyrod.springailens.model.AiCallEvent;
import com.bluntyrod.springailens.model.AnomalyReport;
import com.bluntyrod.springailens.util.EventStore;
import com.bluntyrod.springailens.util.store.InMemoryEventStore;

class AiLensEndpointTest {

    @Test
    void reportIsEmptyWhenNoCallsMade() {
        EventStore store = new InMemoryEventStore(10);
        AiLensEndpoint endpoint = new AiLensEndpoint(store);

        AiLensEndpoint.AiLensReport report = endpoint.report();

        assertThat(report.totalCalls()).isEqualTo(0);
        assertThat(report.avgLatencyMs()).isEqualTo(0);
        assertThat(report.totalTokens()).isEqualTo(0);
        assertThat(report.calls()).isEmpty();
    }

    @Test
    void reportAggregatesCorrectly() {
        EventStore store = new InMemoryEventStore(10);
        AiLensEndpoint endpoint = new AiLensEndpoint(store);

        store.add(new AiCallEvent(UUID.randomUUID().toString(), Instant.now(),
                "OpenAiChatModel", "prompt 1", "response 1", 100, 10, 20, AnomalyReport.none(), null));
        store.add(new AiCallEvent(UUID.randomUUID().toString(), Instant.now(),
                "OpenAiChatModel", "prompt 2", "response 2", 200, 15, 25, AnomalyReport.none(), null));        AiLensEndpoint.AiLensReport report = endpoint.report();

        assertThat(report.totalCalls()).isEqualTo(2);
        assertThat(report.avgLatencyMs()).isEqualTo(150);
        assertThat(report.totalTokens()).isEqualTo(70); // 10+20+15+25
    }

    @Test
    void endpointIdIsValidForActuator() {
        // Actuator endpoint ids must match [a-zA-Z][a-zA-Z0-9]* — no hyphens or other
        // punctuation — or Spring Boot logs "contains invalid characters" at startup
        // and refuses to expose the endpoint correctly.
        Endpoint annotation = AiLensEndpoint.class.getAnnotation(Endpoint.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.id()).matches("[a-zA-Z][a-zA-Z0-9]*");
    }
}
