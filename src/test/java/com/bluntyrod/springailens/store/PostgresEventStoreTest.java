package com.bluntyrod.springailens.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.bluntyrod.springailens.config.AiLensProperties;
import com.bluntyrod.springailens.model.AiCallEvent;
import com.bluntyrod.springailens.model.AnomalyReport;
import com.bluntyrod.springailens.util.store.PostgresEventStore;

@ExtendWith(MockitoExtension.class)
class PostgresEventStoreTest {

    @Mock
    private JdbcTemplate jdbc;

    private PostgresEventStore store;
    private AiLensProperties properties;

    @BeforeEach
    void setUp() {
        // initTable() calls jdbc.execute(CREATE_TABLE) on construction — stub it
        doNothing().when(jdbc).execute(anyString());

        properties = new AiLensProperties();
        store = new PostgresEventStore(jdbc, properties);
    }

    private AiCallEvent event(String prompt) {
        return new AiCallEvent(
                UUID.randomUUID().toString(), Instant.now(),
                "OllamaChatModel", prompt, "response",
                100, 10, 20, AnomalyReport.none(), null
        );
    }

    // --- construction ---

    @Test
    void constructorCreatesTableOnStartup() {
        // called once in setUp via constructor
        verify(jdbc, times(1)).execute(contains("CREATE TABLE IF NOT EXISTS ai_lens_events"));
    }

    // --- add() ---

    @Test
    void addEnqueuesEventWithoutImmediateDbWrite() {
        // add() only puts onto the queue — no jdbc interaction expected here
        store.add(event("queued prompt"));

        // batchUpdate is only called by the scheduler flush, not inline
        verify(jdbc, never()).batchUpdate(anyString(), anyList(), anyInt(), any());
    }

    @Test
    void addDoesNotThrowForValidEvent() {
        // smoke test — should never throw
        store.add(event("safe prompt"));
    }

    // --- getAll() ---

    @Test
    void getAllDelegatesToJdbcQuery() {
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of());

        store.getAll();

        verify(jdbc).query(contains("SELECT * FROM ai_lens_events"), any(RowMapper.class));
    }

    @Test
    void getAllReturnsEventsFromJdbc() {
        AiCallEvent e1 = event("prompt 1");
        AiCallEvent e2 = event("prompt 2");
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(e1, e2));

        List<AiCallEvent> result = store.getAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).prompt()).isEqualTo("prompt 1");
    }

    // --- getRecent() ---

    @Test
    void getRecentPassesLimitToJdbc() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(5))).thenReturn(List.of());

        store.getRecent(5);

        verify(jdbc).query(contains("LIMIT"), any(RowMapper.class), eq(5));
    }

    // --- count() ---

    @Test
    void countQueriesCountFromDb() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(42L);

        assertThat(store.count()).isEqualTo(42);
    }

    @Test
    void countReturnsZeroWhenJdbcReturnsNull() {
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(null);

        assertThat(store.count()).isEqualTo(0);
    }

    // --- clear() ---

    @Test
    void clearExecutesDeleteAll() {
        store.clear();

        verify(jdbc).execute("DELETE FROM ai_lens_events");
    }
}
