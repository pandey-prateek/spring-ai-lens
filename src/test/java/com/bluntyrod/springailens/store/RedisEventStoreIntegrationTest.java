package com.bluntyrod.springailens.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.bluntyrod.springailens.config.AiLensProperties;
import com.bluntyrod.springailens.model.AiCallEvent;
import com.bluntyrod.springailens.model.AnomalyReport;
import com.bluntyrod.springailens.model.AnomalyType;
import com.bluntyrod.springailens.model.DiffStatus;
import com.bluntyrod.springailens.model.PromptDiffResult;
import com.bluntyrod.springailens.util.store.RedisEventStore;
import com.redis.testcontainers.RedisContainer;

@Testcontainers
@DisabledIfSystemProperty(named = "skip.integration.tests", matches = "true")
class RedisEventStoreIntegrationTest {

    @Container
    static RedisContainer redis = new RedisContainer(
            RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG));

    private RedisEventStore store;
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                redis.getHost(), redis.getFirstMappedPort());
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(factory);
        redisTemplate.afterPropertiesSet();

        AiLensProperties properties = new AiLensProperties();
        store = new RedisEventStore(redisTemplate, properties);
        store.clear();
    }

    private AiCallEvent event(String prompt) {
        return new AiCallEvent(
                UUID.randomUUID().toString(), Instant.now(),
                "OllamaChatModel", prompt, "response",
                100, 10, 20, AnomalyReport.none(), null
        );
    }

    @Test
    void addAndRetrieveSingleEvent() {
        store.add(event("hello world"));

        List<AiCallEvent> events = store.getAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).prompt()).isEqualTo("hello world");
        assertThat(events.get(0).model()).isEqualTo("OllamaChatModel");
        assertThat(events.get(0).latencyMs()).isEqualTo(100);
    }

    @Test
    void getAllReturnsEventsInChronologicalOrder() {
        store.add(event("first"));
        store.add(event("second"));
        store.add(event("third"));

        List<AiCallEvent> events = store.getAll();
        assertThat(events).hasSize(3);
        assertThat(events.get(0).prompt()).isEqualTo("first");
        assertThat(events.get(2).prompt()).isEqualTo("third");
    }

    @Test
    void getAllReturnsEmptyWhenNoEvents() {
        assertThat(store.getAll()).isEmpty();
    }

    @Test
    void getRecentReturnsNewestFirst() {
        store.add(event("oldest"));
        store.add(event("middle"));
        store.add(event("newest"));

        List<AiCallEvent> recent = store.getRecent(2);
        assertThat(recent).hasSize(2);
        assertThat(recent.get(0).prompt()).isEqualTo("newest");
        assertThat(recent.get(1).prompt()).isEqualTo("middle");
    }

    @Test
    void getRecentRespectsLimit() {
        for (int i = 0; i < 10; i++) {
            store.add(event("prompt " + i));
        }
        assertThat(store.getRecent(3)).hasSize(3);
    }

    @Test
    void getRecentReturnsAllWhenLimitExceedsCount() {
        store.add(event("only one"));
        assertThat(store.getRecent(50)).hasSize(1);
    }

    @Test
    void countReflectsNumberOfStoredEvents() {
        assertThat(store.count()).isEqualTo(0);
        store.add(event("a"));
        store.add(event("b"));
        assertThat(store.count()).isEqualTo(2);
    }

    @Test
    void clearRemovesAllEvents() {
        store.add(event("to be cleared"));
        store.clear();

        assertThat(store.getAll()).isEmpty();
        assertThat(store.count()).isEqualTo(0);
    }

    @Test
    void exceedingBufferSizeEvictsOldestEvents() {
        AiLensProperties smallBuffer = new AiLensProperties();
        smallBuffer.setBufferSize(3);
        RedisEventStore smallStore = new RedisEventStore(redisTemplate, smallBuffer);
        smallStore.clear();

        smallStore.add(event("first"));
        smallStore.add(event("second"));
        smallStore.add(event("third"));
        smallStore.add(event("fourth"));

        assertThat(smallStore.count()).isEqualTo(3);
        assertThat(smallStore.getAll().stream().map(AiCallEvent::prompt))
                .doesNotContain("first")
                .contains("second", "third", "fourth");
    }

    @Test
    void serializesAndDeserializesAllFields() {
        AiCallEvent original = new AiCallEvent(
                "fixed-id",
                Instant.parse("2025-01-15T10:30:00Z"),
                "OpenAiChatModel",
                "What is the capital of France?",
                "Paris.",
                250L, 15, 5,
                AnomalyReport.of(AnomalyType.LATENCY_SPIKE, "spike!"),
                new PromptDiffResult(DiffStatus.CHANGED, "abc123", "def456", "old", "new")
        );

        store.add(original);

        AiCallEvent retrieved = store.getAll().get(0);
        assertThat(retrieved.id()).isEqualTo("fixed-id");
        assertThat(retrieved.timestamp()).isEqualTo(Instant.parse("2025-01-15T10:30:00Z"));
        assertThat(retrieved.model()).isEqualTo("OpenAiChatModel");
        assertThat(retrieved.prompt()).isEqualTo("What is the capital of France?");
        assertThat(retrieved.response()).isEqualTo("Paris.");
        assertThat(retrieved.latencyMs()).isEqualTo(250L);
        assertThat(retrieved.promptTokens()).isEqualTo(15);
        assertThat(retrieved.completionTokens()).isEqualTo(5);
    }

    @Test
    void handlesEventWithNullAnomalyAndDiff() {
        AiCallEvent event = new AiCallEvent(
                UUID.randomUUID().toString(), Instant.now(),
                "model", "prompt", "response",
                100, 10, 20, null, null
        );

        store.add(event);

        assertThat(store.getAll()).hasSize(1);
    }
}
