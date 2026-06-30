package com.bluntyrod.springailens.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.bluntyrod.springailens.config.AiLensProperties;
import com.bluntyrod.springailens.model.AiCallEvent;
import com.bluntyrod.springailens.model.AnomalyReport;
import com.bluntyrod.springailens.util.store.RedisEventStore;

@ExtendWith(MockitoExtension.class)
class RedisEventStoreTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ListOperations<String, String> listOps;

    private RedisEventStore store;
    private AiLensProperties properties;

    @BeforeEach
    void setUp() {
        // Use lenient() so tests that don't call opsForList() (e.g. clearDeletesKey)
        // don't trip UnnecessaryStubbingException in Mockito strict mode.
        lenient().when(redis.opsForList()).thenReturn(listOps);
        properties = new AiLensProperties();
        store = new RedisEventStore(redis, properties);
    }

    private AiCallEvent event(String prompt) {
        return new AiCallEvent(
                UUID.randomUUID().toString(), Instant.now(),
                "OllamaChatModel", prompt, "response",
                100, 10, 20, AnomalyReport.none(), null
        );
    }

    // --- add() ---

    @Test
    void addPushesSerializedJsonToRedis() {
        store.add(event("hello"));

        verify(listOps).leftPush(eq("ai-lens:events"), anyString());
    }

    @Test
    void addTrimsListToBufferSize() {
        store.add(event("hello"));

        verify(listOps).trim("ai-lens:events", 0, properties.getBufferSize() - 1);
    }

    @Test
    void addSetsTtlOnKey() {
        store.add(event("hello"));

        verify(redis).expire("ai-lens:events",
                Duration.ofDays(properties.getStorage().getRedis().getTtlDays()));
    }

    @Test
    void addUsesCustomKeyPrefix() {
        properties.getStorage().getRedis().setKeyPrefix("my-app");
        store = new RedisEventStore(redis, properties);

        store.add(event("hello"));

        verify(listOps).leftPush(eq("my-app:events"), anyString());
    }

    // --- getAll() ---

    @Test
    void getAllReturnsEmptyListWhenRedisReturnsNull() {
        when(listOps.range(anyString(), anyLong(), anyLong())).thenReturn(null);

        assertThat(store.getAll()).isEmpty();
    }

    @Test
    void getAllDeserializesAndReversesOrder() {
        AiCallEvent e1 = event("first");
        AiCallEvent e2 = event("second");

        // Redis stores newest-first (leftPush), so range returns [e2_json, e1_json].
        // getAll() calls Collections.reverse() — must be a mutable list.
        String json1 = serialize(e1);
        String json2 = serialize(e2);
        when(listOps.range("ai-lens:events", 0, -1))
                .thenReturn(new ArrayList<>(List.of(json2, json1)));

        List<AiCallEvent> result = store.getAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).prompt()).isEqualTo("first");
        assertThat(result.get(1).prompt()).isEqualTo("second");
    }

    @Test
    void getAllFiltersOutCorruptEntries() {
        when(listOps.range("ai-lens:events", 0, -1))
                .thenReturn(new ArrayList<>(List.of("{invalid-json}", serialize(event("ok")))));

        List<AiCallEvent> result = store.getAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).prompt()).isEqualTo("ok");
    }

    // --- getRecent() ---

    @Test
    void getRecentQueriesWithLimit() {
        when(listOps.range("ai-lens:events", 0, 4)).thenReturn(new ArrayList<>());

        store.getRecent(5);

        verify(listOps).range("ai-lens:events", 0, 4);
    }

    @Test
    void getRecentReturnsEmptyListWhenRedisReturnsNull() {
        when(listOps.range(anyString(), anyLong(), anyLong())).thenReturn(null);

        assertThat(store.getRecent(10)).isEmpty();
    }

    // --- count() ---

    @Test
    void countReturnsListSize() {
        when(listOps.size("ai-lens:events")).thenReturn(7L);

        assertThat(store.count()).isEqualTo(7);
    }

    @Test
    void countReturnsZeroWhenRedisReturnsNull() {
        when(listOps.size("ai-lens:events")).thenReturn(null);

        assertThat(store.count()).isEqualTo(0);
    }

    // --- clear() ---

    @Test
    void clearDeletesKey() {
        // clear() only calls redis.delete() — no opsForList() involved
        store.clear();

        verify(redis).delete("ai-lens:events");
    }

    // --- helpers ---

    private String serialize(AiCallEvent event) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                    .writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
