package io.ailens.springailens.util.diff;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PromptHasherTest {

    private final PromptHasher hasher = new PromptHasher();

    @Test
    void samePromptProducesSameHash() {
        assertThat(hasher.hash("What is Java?"))
                .isEqualTo(hasher.hash("What is Java?"));
    }

    @Test
    void differentPromptsProduceDifferentHashes() {
        assertThat(hasher.hash("What is Java?"))
                .isNotEqualTo(hasher.hash("What is Python?"));
    }

    @Test
    void normalizesNumbers() {
        assertThat(hasher.normalize("Order 123 is ready"))
                .isEqualTo("order n is ready");
    }

    @Test
    void normalizesEmails() {
        assertThat(hasher.normalize("Contact user@example.com"))
                .isEqualTo("contact email");
    }
}
