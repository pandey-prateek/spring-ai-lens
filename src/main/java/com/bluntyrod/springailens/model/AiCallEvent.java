package com.bluntyrod.springailens.model;

import java.time.Instant;

/**
 * Immutable record of a single {@code ChatModel} invocation captured by spring-ai-lens.
 *
 * @param id               unique identifier for this call (UUID)
 * @param timestamp        when the call completed
 * @param model            simple class name of the {@code ChatModel} implementation, e.g. {@code OllamaChatModel}
 * @param prompt           the raw prompt text sent to the model
 * @param response         the raw response text returned by the model
 * @param latencyMs        wall-clock duration of the call in milliseconds
 * @param promptTokens     prompt token count reported by the model, if available
 * @param completionTokens completion token count reported by the model, if available
 * @param anomaly          anomaly analysis result; {@link AnomalyReport#none()} if nothing was flagged
 * @param diff             prompt diff result against the previous call for this model; {@code null} if not tracked
 */
public record AiCallEvent(
        String id,
        Instant timestamp,
        String model,
        String prompt,
        String response,
        long latencyMs,
        int promptTokens,
        int completionTokens,
        AnomalyReport anomaly,
        PromptDiffResult diff
) {}
