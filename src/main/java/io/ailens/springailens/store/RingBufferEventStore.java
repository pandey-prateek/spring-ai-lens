package io.ailens.springailens.store;

import io.ailens.springailens.model.AiCallEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class RingBufferEventStore {

    private final int capacity;
    private final Deque<AiCallEvent> buffer;

    public RingBufferEventStore(int capacity) {
        this.capacity = capacity;
        this.buffer = new ArrayDeque<>(capacity);
    }

    public synchronized void add(AiCallEvent event) {
        if (buffer.size() >= capacity) {
            buffer.pollFirst();
        }
        buffer.addLast(event);
    }

    public synchronized List<AiCallEvent> getAll() {
        return List.copyOf(buffer);
    }

    public synchronized void clear() {
        buffer.clear();
    }
}