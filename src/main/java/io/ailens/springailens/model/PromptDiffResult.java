package io.ailens.springailens.model;

public record PromptDiffResult(
        DiffStatus status,
        String currentHash,
        String previousHash,
        String previousPrompt,
        String currentPrompt
) {
    public static PromptDiffResult firstSeen(String hash) {
        return new PromptDiffResult(DiffStatus.FIRST_SEEN, hash, null, null, null);
    }

    public static PromptDiffResult unchanged(String hash) {
        return new PromptDiffResult(DiffStatus.UNCHANGED, hash, null, null, null);
    }

    public static PromptDiffResult changed(String prevHash, String currHash,
                                           String prevPrompt, String currPrompt) {
        return new PromptDiffResult(DiffStatus.CHANGED, currHash, prevHash, prevPrompt, currPrompt);
    }

    public boolean hasChanged() {
        return status == DiffStatus.CHANGED;
    }
}
