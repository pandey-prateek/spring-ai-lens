package com.bluntyrod.springailens.util.diff;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bluntyrod.springailens.config.DiffProperties;
import com.bluntyrod.springailens.model.AiCallEvent;

/**
 * Reacts to {@link com.bluntyrod.springailens.model.PromptDiffResult#hasChanged()} events by
 * logging a warning and, if {@code ai-lens.diff.webhook-url} is configured, POSTing a small JSON
 * payload describing the regression to an external endpoint (e.g. Slack incoming webhook, internal
 * alerting service).
 *
 * <p>Webhook delivery is fire-and-forget and best-effort: failures are logged at {@code WARN} and
 * never propagate back into the intercepted call path.
 */
public class PromptRegressionAlerter {

    private static final Logger log = LoggerFactory.getLogger(PromptRegressionAlerter.class);

    private final DiffProperties properties;
    private final HttpClient httpClient;

    public PromptRegressionAlerter(DiffProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * Inspects the event's diff result and, if it represents a prompt template change, logs a
     * warning and dispatches the configured webhook (if any).
     */
    public void onEvent(AiCallEvent event) {
        if (event.diff() == null || !event.diff().hasChanged()) {
            return;
        }

        if (properties.isAlertOnChange()) {
            log.warn("spring-ai-lens: prompt template changed for model '{}' (hash {} -> {})",
                    event.model(), event.diff().previousHash(), event.diff().currentHash());
        }

        String webhookUrl = properties.getWebhookUrl();
        if (webhookUrl != null && !webhookUrl.isBlank()) {
            dispatchWebhook(webhookUrl, event);
        }
    }

    private void dispatchWebhook(String webhookUrl, AiCallEvent event) {
        try {
            String payload = """
                    {"event":"prompt_changed","model":"%s","eventId":"%s","previousHash":"%s","currentHash":"%s","timestamp":"%s"}
                    """.formatted(
                    escape(event.model()),
                    escape(event.id()),
                    escape(event.diff().previousHash()),
                    escape(event.diff().currentHash()),
                    event.timestamp());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(ex -> {
                        log.warn("spring-ai-lens: failed to deliver prompt regression webhook", ex);
                        return null;
                    });
        } catch (Exception ex) {
            log.warn("spring-ai-lens: failed to build prompt regression webhook request", ex);
        }
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
