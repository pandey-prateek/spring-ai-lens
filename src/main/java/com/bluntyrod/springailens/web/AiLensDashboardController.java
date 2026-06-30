package com.bluntyrod.springailens.web;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.TreeSet;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.bluntyrod.springailens.model.AiCallEvent;
import com.bluntyrod.springailens.util.EventStore;
import com.bluntyrod.springailens.util.diff.PromptDiffRenderer;

/**
 * Serves the embedded {@code /ai-lens} HTML dashboard: a live, auto-refreshing table of captured
 * calls with per-model and time-range filtering, plus a side-by-side prompt diff viewer for
 * calls whose prompt template changed (Phase 4).
 */
@RestController
@RequestMapping("/ai-lens")
public class AiLensDashboardController {

    private final EventStore store;

    public AiLensDashboardController(EventStore store) {
        this.store = store;
    }

    /** Convenience overload used by tests and callers that don't need filtering. */
    public String dashboard() {
        return dashboard(null, null, null);
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public String dashboard(@RequestParam(required = false) String model,
                             @RequestParam(required = false) String from,
                             @RequestParam(required = false) String to) {
        List<AiCallEvent> all = store.getAll();

        Instant fromInstant = parseInstant(from);
        Instant toInstant = parseInstant(to);

        List<AiCallEvent> events = all.stream()
                .filter(e -> model == null || model.isBlank() || e.model().equals(model))
                .filter(e -> fromInstant == null || !e.timestamp().isBefore(fromInstant))
                .filter(e -> toInstant == null || !e.timestamp().isAfter(toInstant))
                .toList();

        TreeSet<String> models = new TreeSet<>();
        all.forEach(e -> models.add(e.model()));

        long avgLatency = (long) events.stream()
                .mapToLong(AiCallEvent::latencyMs)
                .average()
                .orElse(0);

        int totalTokens = events.stream()
                .mapToInt(e -> e.promptTokens() + e.completionTokens())
                .sum();

        StringBuilder rows = new StringBuilder();
        List<AiCallEvent> reversed = events.reversed();
        for (AiCallEvent e : reversed) {
            String anomalyBadge = "";
            if (e.anomaly() != null && e.anomaly().hasAnomaly()) {
                anomalyBadge = "<span class='badge'>⚠ %s</span>".formatted(e.anomaly().message());
            }
            String diffBadge = "";
            if (e.diff() != null && e.diff().hasChanged()) {
                diffBadge = "<a class='diff-badge' href='/ai-lens/diff/%s'>⟳ prompt changed</a>".formatted(e.id());
            }
            rows.append("""
        <tr class="%s">
            <td>%s</td>
            <td class="prompt">%s</td>
            <td class="response">%s</td>
            <td>%d ms</td>
            <td>%d / %d</td>
            <td>%s</td>
            <td>%s</td>
            <td>%s</td>
        </tr>
    """.formatted(
                    e.anomaly() != null && e.anomaly().hasAnomaly() ? "anomaly" : "",
                    e.model(),
                    truncate(e.prompt(), 80),
                    truncate(e.response(), 80),
                    e.latencyMs(),
                    e.promptTokens(),
                    e.completionTokens(),
                    e.timestamp().toString(),
                    anomalyBadge,
                    diffBadge
            ));
        }

        StringBuilder modelOptions = new StringBuilder("<option value=\"\">All models</option>");
        for (String m : models) {
            modelOptions.append("<option value=\"%s\"%s>%s</option>".formatted(
                    m, m.equals(model) ? " selected" : "", m));
        }

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta http-equiv="refresh" content="5">
                <title>spring-ai-lens</title>
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body { font-family: -apple-system, sans-serif; background: #f5f5f5; color: #222; }
                    header { background: #1a1a2e; color: white; padding: 16px 32px; display: flex; align-items: center; gap: 12px; }
                    header h1 { font-size: 18px; font-weight: 600; }
                    header span { font-size: 12px; opacity: 0.6; }
                    .stats { display: flex; gap: 16px; padding: 24px 32px; }
                    .stat { background: white; border-radius: 8px; padding: 16px 24px; flex: 1; border: 1px solid #e0e0e0; }
                    .stat .label { font-size: 12px; color: #888; margin-bottom: 4px; }
                    .stat .value { font-size: 28px; font-weight: 600; }
                    .filters { display: flex; gap: 12px; padding: 0 32px 16px; align-items: center; }
                    .filters select, .filters input { padding: 6px 10px; border: 1px solid #ddd; border-radius: 6px; font-size: 13px; }
                    .filters button { padding: 6px 14px; border: none; border-radius: 6px; background: #1a1a2e; color: white; font-size: 13px; cursor: pointer; }
                    .table-wrap { padding: 0 32px 32px; }
                    table { width: 100%%; border-collapse: collapse; background: white; border-radius: 8px; overflow: hidden; border: 1px solid #e0e0e0; }
                    th { background: #f9f9f9; text-align: left; padding: 12px 16px; font-size: 12px; color: #555; border-bottom: 1px solid #e0e0e0; }
                    td { padding: 12px 16px; font-size: 13px; border-bottom: 1px solid #f0f0f0; vertical-align: top; }
                    tr:last-child td { border-bottom: none; }
                    tr:hover td { background: #fafafa; }
                    .prompt, .response { max-width: 300px; color: #444; }
                    .empty { text-align: center; padding: 48px; color: #aaa; }
                    tr.anomaly td { background: #fff8f0; }
                    .badge { background: #ff9800; color: white; padding: 2px 8px; border-radius: 4px; font-size: 11px; white-space: nowrap; }
                    .diff-badge { background: #2196f3; color: white; padding: 2px 8px; border-radius: 4px; font-size: 11px; text-decoration: none; }
                </style>
            </head>
            <body>
                <header>
                    <h1>spring-ai-lens</h1>
                    <span>auto-refreshes every 5s</span>
                </header>
                <div class="stats">
                    <div class="stat"><div class="label">Total calls</div><div class="value">%d</div></div>
                    <div class="stat"><div class="label">Avg latency</div><div class="value">%d ms</div></div>
                    <div class="stat"><div class="label">Total tokens</div><div class="value">%d</div></div>
                    <div class="stat"><div class="label">Models seen</div><div class="value">%d</div></div>
                </div>
                <form class="filters" method="get" action="/ai-lens">
                    <select name="model">%s</select>
                    <input type="text" name="from" placeholder="from (ISO instant)" value="%s">
                    <input type="text" name="to" placeholder="to (ISO instant)" value="%s">
                    <button type="submit">Filter</button>
                </form>
                <div class="table-wrap">
                    <table>
                        <thead>
                            <tr>
                                <th>Model</th>
                                        <th>Prompt</th>
                                        <th>Response</th>
                                        <th>Latency</th>
                                        <th>Tokens (in/out)</th>
                                        <th>Timestamp</th>
                                        <th>Anomaly</th>
                                                <th>Diff</th>
                            </tr>
                        </thead>
                        <tbody>
                            %s
                        </tbody>
                    </table>
                    %s
                </div>
            </body>
            </html>
        """.formatted(
                events.size(),
                avgLatency,
                totalTokens,
                models.size(),
                modelOptions,
                from == null ? "" : from,
                to == null ? "" : to,
                rows.toString(),
                events.isEmpty() ? "<p class=\"empty\">No LLM calls captured yet.</p>" : ""
        );
    }

    /** Side-by-side prompt diff viewer for a single call whose prompt template changed (Phase 4). */
    @GetMapping(value = "/diff/{eventId}", produces = MediaType.TEXT_HTML_VALUE)
    public String diff(@PathVariable String eventId) {
        AiCallEvent event = store.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No event with id " + eventId));

        if (event.diff() == null || !event.diff().hasChanged()) {
            return """
                <!DOCTYPE html><html><head><meta charset="UTF-8"><title>spring-ai-lens diff</title></head>
                <body style="font-family:-apple-system,sans-serif;padding:32px;">
                    <p>No prompt change recorded for this event.</p>
                    <p><a href="/ai-lens">&larr; back to dashboard</a></p>
                </body></html>
                """;
        }

        String previousHtml = PromptDiffRenderer.renderPrevious(event.diff().previousPrompt(), event.diff().currentPrompt());
        String currentHtml = PromptDiffRenderer.renderCurrent(event.diff().previousPrompt(), event.diff().currentPrompt());

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>spring-ai-lens — prompt diff</title>
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body { font-family: -apple-system, sans-serif; background: #f5f5f5; color: #222; padding: 32px; }
                    h1 { font-size: 18px; margin-bottom: 4px; }
                    .meta { font-size: 12px; color: #888; margin-bottom: 24px; }
                    .panes { display: flex; gap: 16px; }
                    .pane { flex: 1; background: white; border: 1px solid #e0e0e0; border-radius: 8px; padding: 16px; white-space: pre-wrap; word-break: break-word; font-size: 13px; line-height: 1.5; }
                    .pane h2 { font-size: 13px; color: #555; margin-bottom: 8px; }
                    ins { background: #d4f8d4; text-decoration: none; }
                    del { background: #f8d4d4; }
                    a.back { display: inline-block; margin-top: 24px; color: #1a1a2e; }
                </style>
            </head>
            <body>
                <h1>Prompt diff — %s</h1>
                <div class="meta">model: %s · hash %s &rarr; %s</div>
                <div class="panes">
                    <div class="pane"><h2>Previous</h2>%s</div>
                    <div class="pane"><h2>Current</h2>%s</div>
                </div>
                <a class="back" href="/ai-lens">&larr; back to dashboard</a>
            </body>
            </html>
        """.formatted(
                event.id(), event.model(),
                event.diff().previousHash(), event.diff().currentHash(),
                previousHtml, currentHtml
        );
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }
}
