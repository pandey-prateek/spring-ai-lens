package com.bluntyrod.springailens.util.diff;

import java.util.concurrent.ConcurrentHashMap;

import com.bluntyrod.springailens.model.PromptDiffResult;

public class PromptDiffTracker {

    private final PromptHasher hasher = new PromptHasher();
    private final ConcurrentHashMap<String, PromptVersion> lastVersionByModel = new ConcurrentHashMap<>();

    public PromptDiffResult track(String model, String prompt) {
        String hash = hasher.hash(prompt);
        PromptVersion previous = lastVersionByModel.put(model, new PromptVersion(hash, prompt));

        if (previous == null) {
            return PromptDiffResult.firstSeen(hash);
        }

        if (previous.hash().equals(hash)) {
            return PromptDiffResult.unchanged(hash);
        }

        return PromptDiffResult.changed(previous.hash(), hash, previous.prompt(), prompt);
    }

    public record PromptVersion(String hash, String prompt) {}
}
