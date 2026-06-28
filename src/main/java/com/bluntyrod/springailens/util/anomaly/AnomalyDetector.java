package com.bluntyrod.springailens.util.anomaly;

import java.util.List;

import com.bluntyrod.springailens.config.AnomalyProperties;
import com.bluntyrod.springailens.model.AiCallEvent;
import com.bluntyrod.springailens.model.AnomalyReport;
import com.bluntyrod.springailens.model.AnomalyType;
import com.bluntyrod.springailens.util.EventStore;

public class AnomalyDetector {

    private final EventStore store;
    private final AnomalyProperties config;

    public AnomalyDetector(EventStore store, AnomalyProperties config) {
        this.store = store;
        this.config = config;
    }

    public AnomalyReport analyze(AiCallEvent event) {
        List<AiCallEvent> history = store.getAll();

        if (history.size() < config.getMinBaselineCalls()) {
            return AnomalyReport.none();
        }

        double avgLatency = history.stream()
                .mapToLong(AiCallEvent::latencyMs)
                .average()
                .orElse(0);

        double avgTokens = history.stream()
                .mapToInt(e -> e.promptTokens() + e.completionTokens())
                .average()
                .orElse(0);

        boolean latencySpike = avgLatency > 0 &&
                event.latencyMs() > avgLatency * config.getLatencyThreshold();

        boolean tokenSpike = avgTokens > 0 &&
                (event.promptTokens() + event.completionTokens()) > avgTokens * config.getTokenThreshold();

        if (latencySpike && tokenSpike) {
            return AnomalyReport.of(AnomalyType.LATENCY_AND_TOKEN_SPIKE,
                    "Latency %.0fms (avg %.0fms) and tokens %d (avg %.0f) both spiked"
                            .formatted(
                                    (double) event.latencyMs(), avgLatency,
                                    event.promptTokens() + event.completionTokens(), avgTokens));
        } else if (latencySpike) {
            return AnomalyReport.of(AnomalyType.LATENCY_SPIKE,
                    "Latency %.0fms is %.1fx above avg %.0fms"
                            .formatted((double) event.latencyMs(),
                                    event.latencyMs() / avgLatency, avgLatency));
        } else if (tokenSpike) {
            return AnomalyReport.of(AnomalyType.TOKEN_SPIKE,
                    "Token count %d is %.1fx above avg %.0f"
                            .formatted(event.promptTokens() + event.completionTokens(),
                                    (event.promptTokens() + event.completionTokens()) / avgTokens, avgTokens));
        }

        return AnomalyReport.none();
    }
}
