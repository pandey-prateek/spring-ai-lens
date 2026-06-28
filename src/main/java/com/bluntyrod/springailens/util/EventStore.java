package com.bluntyrod.springailens.util;

import java.util.List;

import com.bluntyrod.springailens.model.AiCallEvent;

public interface EventStore {

    void add(AiCallEvent event);

    List<AiCallEvent> getAll();

    List<AiCallEvent> getRecent(int limit);

    long count();

    void clear();
}
