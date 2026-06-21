package io.ailens.springailens.util.diff;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.ailens.springailens.model.DiffStatus;
import io.ailens.springailens.model.PromptDiffResult;

class PromptDiffTrackerTest {

    private final PromptDiffTracker tracker = new PromptDiffTracker();

    @Test
    void firstCallIsMarkedFirstSeen() {
        PromptDiffResult result = tracker.track("OllamaChatModel", "What is Java?");

        assertThat(result.status()).isEqualTo(DiffStatus.FIRST_SEEN);
        assertThat(result.hasChanged()).isFalse();
    }

    @Test
    void samePromptIsUnchanged() {
        tracker.track("OllamaChatModel", "What is Java?");
        PromptDiffResult result = tracker.track("OllamaChatModel", "What is Java?");

        assertThat(result.status()).isEqualTo(DiffStatus.UNCHANGED);
        assertThat(result.hasChanged()).isFalse();
    }

    @Test
    void differentPromptIsDetectedAsChanged() {
        tracker.track("OllamaChatModel", "What is Java?");
        PromptDiffResult result = tracker.track("OllamaChatModel", "What is Python?");

        assertThat(result.status()).isEqualTo(DiffStatus.CHANGED);
        assertThat(result.hasChanged()).isTrue();
        assertThat(result.previousPrompt()).isEqualTo("What is Java?");
        assertThat(result.currentPrompt()).isEqualTo("What is Python?");
    }

    @Test
    void tracksModelsIndependently() {
        tracker.track("ModelA", "What is Java?");
        tracker.track("ModelB", "What is Java?");

        PromptDiffResult resultA = tracker.track("ModelA", "What is Python?");
        PromptDiffResult resultB = tracker.track("ModelB", "What is Java?");

        assertThat(resultA.hasChanged()).isTrue();
        assertThat(resultB.hasChanged()).isFalse();
    }
}
