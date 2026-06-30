package com.bluntyrod.springailens.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.bluntyrod.springailens.model.AiCallEvent;
import com.bluntyrod.springailens.model.AnomalyReport;
import com.bluntyrod.springailens.model.PromptDiffResult;
import com.bluntyrod.springailens.util.EventStore;
import com.bluntyrod.springailens.util.store.InMemoryEventStore;

class AiLensDashboardControllerTest {

    @Test
    void dashboardShowsEmptyStateWhenNoEvents() {
        EventStore store = new InMemoryEventStore(10);
        AiLensDashboardController controller = new AiLensDashboardController(store);

        String html = controller.dashboard();

        assertThat(html).contains("No LLM calls captured yet");
        assertThat(html).contains("Total calls");
    }

    @Test
    void dashboardRendersEventData() {
        EventStore store = new InMemoryEventStore(10);
        store.add(new AiCallEvent(UUID.randomUUID().toString(), Instant.now(),
                "OpenAiChatModel", "What is Java?", "A programming language", 42, 5, 10, AnomalyReport.none(), null));

        AiLensDashboardController controller = new AiLensDashboardController(store);
        String html = controller.dashboard();

        assertThat(html).contains("OpenAiChatModel");
        assertThat(html).contains("What is Java?");
        assertThat(html).contains("A programming language");
        assertThat(html).contains("42 ms");
    }

    @Test
    void dashboardTruncatesLongPrompts() {
        EventStore store = new InMemoryEventStore(10);
        String longPrompt = "a".repeat(200);
        store.add(new AiCallEvent(UUID.randomUUID().toString(), Instant.now(),
                "OpenAiChatModel", longPrompt, "response", 10, 5, 5, AnomalyReport.none(), null));
        AiLensDashboardController controller = new AiLensDashboardController(store);
        String html = controller.dashboard();

        assertThat(html).contains("…");
        assertThat(html).doesNotContain(longPrompt);
    }

    @Test
    void dashboardFiltersByModel() {
        EventStore store = new InMemoryEventStore(10);
        store.add(new AiCallEvent(UUID.randomUUID().toString(), Instant.now(),
                "OpenAiChatModel", "openai prompt", "response", 10, 5, 5, AnomalyReport.none(), null));
        store.add(new AiCallEvent(UUID.randomUUID().toString(), Instant.now(),
                "OllamaChatModel", "ollama prompt", "response", 10, 5, 5, AnomalyReport.none(), null));

        AiLensDashboardController controller = new AiLensDashboardController(store);
        String html = controller.dashboard("OllamaChatModel", null, null);

        assertThat(html).contains("ollama prompt");
        assertThat(html).doesNotContain("openai prompt");
        assertThat(html).contains("Models seen");
    }

    @Test
    void dashboardFiltersByTimeRange() {
        EventStore store = new InMemoryEventStore(10);
        Instant past = Instant.now().minusSeconds(3600);
        Instant now = Instant.now();
        store.add(new AiCallEvent(UUID.randomUUID().toString(), past,
                "OpenAiChatModel", "old prompt", "response", 10, 5, 5, AnomalyReport.none(), null));
        store.add(new AiCallEvent(UUID.randomUUID().toString(), now,
                "OpenAiChatModel", "new prompt", "response", 10, 5, 5, AnomalyReport.none(), null));

        AiLensDashboardController controller = new AiLensDashboardController(store);
        String html = controller.dashboard(null, now.minusSeconds(60).toString(), null);

        assertThat(html).contains("new prompt");
        assertThat(html).doesNotContain("old prompt");
    }

    @Test
    void diffViewerReturnsNotFoundForUnknownEvent() {
        EventStore store = new InMemoryEventStore(10);
        AiLensDashboardController controller = new AiLensDashboardController(store);

        assertThatThrownBy(() -> controller.diff("missing-id"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void diffViewerRendersSideBySideComparisonWhenPromptChanged() {
        EventStore store = new InMemoryEventStore(10);
        String id = UUID.randomUUID().toString();
        PromptDiffResult diff = PromptDiffResult.changed("oldHash", "newHash", "old prompt text", "new prompt text");
        store.add(new AiCallEvent(id, Instant.now(), "OpenAiChatModel",
                "new prompt text", "response", 10, 5, 5, AnomalyReport.none(), diff));

        AiLensDashboardController controller = new AiLensDashboardController(store);
        String html = controller.diff(id);

        assertThat(html).contains("Previous");
        assertThat(html).contains("Current");
        assertThat(html).contains("oldHash");
        assertThat(html).contains("newHash");
    }

    @Test
    void diffViewerHandlesEventWithoutChange() {
        EventStore store = new InMemoryEventStore(10);
        String id = UUID.randomUUID().toString();
        store.add(new AiCallEvent(id, Instant.now(), "OpenAiChatModel",
                "prompt", "response", 10, 5, 5, AnomalyReport.none(), null));

        AiLensDashboardController controller = new AiLensDashboardController(store);
        String html = controller.diff(id);

        assertThat(html).contains("No prompt change recorded");
    }
}
