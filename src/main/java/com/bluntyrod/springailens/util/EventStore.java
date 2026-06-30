package com.bluntyrod.springailens.util;

import java.util.List;
import java.util.Optional;

import com.bluntyrod.springailens.model.AiCallEvent;

/**
 * Storage abstraction for captured {@link AiCallEvent}s.
 *
 * <p>Implementations are responsible for capping the number of retained events (a ring-buffer /
 * bounded-list semantic) so that long-running applications do not grow storage unbounded.
 * Three implementations ship with spring-ai-lens: {@code InMemoryEventStore} (default, zero
 * extra dependencies), {@code RedisEventStore} and {@code PostgresEventStore}.
 */
public interface EventStore {

    /** Persists a new event, evicting the oldest event if the store is at capacity. */
    void add(AiCallEvent event);

    /** Returns all currently retained events, oldest first. */
    List<AiCallEvent> getAll();

    /** Returns at most {@code limit} of the most recently retained events, oldest first. */
    List<AiCallEvent> getRecent(int limit);

    /** Returns the number of events currently retained. */
    long count();

    /** Removes all retained events. */
    void clear();

    /**
     * Looks up a single event by its id.
     *
     * <p>Default implementation performs a linear scan over {@link #getAll()}; implementations
     * backed by an indexed store (e.g. Redis, Postgres) may override this for efficiency.
     *
     * @param id the {@link AiCallEvent#id()} to search for
     * @return the matching event, or empty if no event with that id is currently retained
     */
    default Optional<AiCallEvent> findById(String id) {
        return getAll().stream().filter(e -> e.id().equals(id)).findFirst();
    }
}
