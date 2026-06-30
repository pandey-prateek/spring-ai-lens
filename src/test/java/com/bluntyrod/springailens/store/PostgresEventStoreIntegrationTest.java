package com.bluntyrod.springailens.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.bluntyrod.springailens.config.AiLensProperties;
import com.bluntyrod.springailens.model.AiCallEvent;
import com.bluntyrod.springailens.model.AnomalyReport;
import com.bluntyrod.springailens.model.AnomalyType;
import com.bluntyrod.springailens.util.store.PostgresEventStore;

@Testcontainers
@DisabledIfSystemProperty(named = "skip.integration.tests", matches = "true")
class PostgresEventStoreIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private PostgresEventStore store;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());

        jdbc = new JdbcTemplate(ds);

        AiLensProperties properties = new AiLensProperties();
        properties.getStorage().getPostgres().setFlushIntervalMs(50);
        properties.getStorage().getPostgres().setBatchSize(50);

        store = new PostgresEventStore(jdbc, properties);
        store.clear();
    }

    private AiCallEvent event(String prompt) {
        return new AiCallEvent(
                UUID.randomUUID().toString(), Instant.now(),
                "OllamaChatModel", prompt, "response",
                100, 10, 20, AnomalyReport.none(), null
        );
    }

    private void awaitCount(long expected) {
        await().atMost(2, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(60))
                .until(() -> store.count() == expected);
    }

    @Test
    void addAndRetrieveSingleEvent() {
        store.add(event("hello postgres"));
        awaitCount(1);

        List<AiCallEvent> events = store.getAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).prompt()).isEqualTo("hello postgres");
        assertThat(events.get(0).model()).isEqualTo("OllamaChatModel");
        assertThat(events.get(0).latencyMs()).isEqualTo(100);
    }

    @Test
    void getAllReturnsEventsNewestFirst() {
        store.add(event("first"));
        store.add(event("second"));
        store.add(event("third"));
        awaitCount(3);

        List<AiCallEvent> events = store.getAll();
        assertThat(events).hasSize(3);
        // ORDER BY timestamp DESC
        assertThat(events.get(0).prompt()).isEqualTo("third");
        assertThat(events.get(2).prompt()).isEqualTo("first");
    }

    @Test
    void getAllReturnsEmptyListWhenNoEvents() {
        assertThat(store.getAll()).isEmpty();
    }

    @Test
    void getRecentRespectsLimit() {
        for (int i = 0; i < 5; i++) {
            store.add(event("prompt " + i));
        }
        awaitCount(5);

        assertThat(store.getRecent(3)).hasSize(3);
    }

    @Test
    void getRecentReturnsNewestEvents() {
        store.add(event("old"));
        store.add(event("newer"));
        store.add(event("newest"));
        awaitCount(3);

        List<AiCallEvent> recent = store.getRecent(2);
        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).prompt()).isEqualTo("newest");
        assertThat(recent.get(1).prompt()).isEqualTo("newer");
    }

    @Test
    void countReflectsNumberOfPersistedEvents() {
        assertThat(store.count()).isEqualTo(0);
        store.add(event("a"));
        store.add(event("b"));
        awaitCount(2);
        assertThat(store.count()).isEqualTo(2);
    }

    @Test
    void clearFlushesQueueAndDeletesAllRows() {
        store.add(event("to delete"));
        store.clear();

        assertThat(store.getAll()).isEmpty();
        assertThat(store.count()).isEqualTo(0);
    }

    @Test
    void duplicateIdIsIgnoredOnConflict() {
        String fixedId = UUID.randomUUID().toString();
        AiCallEvent e = new AiCallEvent(
                fixedId, Instant.now(), "model", "prompt", "response",
                100, 10, 20, AnomalyReport.none(), null
        );

        store.add(e);
        store.add(e); // ON CONFLICT DO NOTHING
        awaitCount(1);

        assertThat(store.count()).isEqualTo(1);
    }

    @Test
    void eventWithAnomalyIsPersistedCorrectly() {
        AiCallEvent e = new AiCallEvent(
                UUID.randomUUID().toString(), Instant.now(),
                "OpenAiChatModel", "prompt", "response",
                500, 10, 20,
                AnomalyReport.of(AnomalyType.LATENCY_SPIKE, "slow call"),
                null
        );

        store.add(e);
        awaitCount(1);

        Boolean hasAnomaly = jdbc.queryForObject(
                "SELECT has_anomaly FROM ai_lens_events WHERE id = ?",
                Boolean.class, e.id());
        String anomalyType = jdbc.queryForObject(
                "SELECT anomaly_type FROM ai_lens_events WHERE id = ?",
                String.class, e.id());

        assertThat(hasAnomaly).isTrue();
        assertThat(anomalyType).isEqualTo("LATENCY_SPIKE");
    }

    @Test
    void eventWithNoAnomalyStoresFalse() {
        AiCallEvent e = event("normal call");
        store.add(e);
        awaitCount(1);

        Boolean hasAnomaly = jdbc.queryForObject(
                "SELECT has_anomaly FROM ai_lens_events WHERE id = ?",
                Boolean.class, e.id());
        assertThat(hasAnomaly).isFalse();
    }

    @Test
    void tableIsCreatedAutomatically() {
        assertThat(store.count()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void multipleEventsAreBatchFlushedCorrectly() {
        int total = 20;
        for (int i = 0; i < total; i++) {
            store.add(event("prompt " + i));
        }
        awaitCount(total);

        assertThat(store.count()).isEqualTo(total);
        assertThat(store.getAll()).hasSize(total);
    }

    @Test
    void promptDiffTextRoundTripsThroughPostgres() {
        com.bluntyrod.springailens.model.PromptDiffResult diff =
                com.bluntyrod.springailens.model.PromptDiffResult.changed(
                        "oldHash", "newHash", "previous prompt text", "current prompt text");

        AiCallEvent e = new AiCallEvent(
                UUID.randomUUID().toString(), Instant.now(),
                "OllamaChatModel", "current prompt text", "response",
                100, 10, 20, AnomalyReport.none(), diff
        );
        store.add(e);
        awaitCount(1);

        AiCallEvent reloaded = store.findById(e.id()).orElseThrow();

        assertThat(reloaded.diff()).isNotNull();
        assertThat(reloaded.diff().hasChanged()).isTrue();
        assertThat(reloaded.diff().previousPrompt()).isEqualTo("previous prompt text");
        assertThat(reloaded.diff().currentPrompt()).isEqualTo("current prompt text");
    }

    @Test
    void migrationAddsPromptTextColumnsToPreExistingTable() {
        // Simulates an older deployment whose table predates the previous_prompt/current_prompt
        // columns: drop them, then constructing a new PostgresEventStore against the same
        // database must add them back without failing.
        jdbc.execute("ALTER TABLE ai_lens_events DROP COLUMN IF EXISTS previous_prompt");
        jdbc.execute("ALTER TABLE ai_lens_events DROP COLUMN IF EXISTS current_prompt");

        AiLensProperties properties = new AiLensProperties();
        properties.getStorage().getPostgres().setFlushIntervalMs(50);
        new PostgresEventStore(jdbc, properties);

        Integer columnCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'ai_lens_events' AND column_name IN ('previous_prompt', 'current_prompt')
                """, Integer.class);
        assertThat(columnCount).isEqualTo(2);
    }
}
