package com.bluntyrod.springailens.actuator;

import java.util.List;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

import com.bluntyrod.springailens.model.AiCallEvent;
import com.bluntyrod.springailens.util.EventStore;

@Endpoint(id = "aiLens")
public class AiLensEndpoint {

    private final EventStore store;

    public AiLensEndpoint(EventStore store) {
        this.store = store;
    }

    @ReadOperation
    public AiLensReport report() {
        List<AiCallEvent> events = store.getAll();

        long avgLatency = (long) events.stream()
                .mapToLong(AiCallEvent::latencyMs)
                .average()
                .orElse(0);

        int totalTokens = events.stream()
                .mapToInt(e -> e.promptTokens() + e.completionTokens())
                .sum();

        return new AiLensReport(events, avgLatency, totalTokens, events.size());
    }

    public record AiLensReport(
            List<AiCallEvent> calls,
            long avgLatencyMs,
            int totalTokens,
            int totalCalls
    ) {}
}
