package com.bluntyrod.springailens.util.diff;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PromptDiffRendererTest {

    @Test
    void rendersInsertionsInCurrent() {
        String html = PromptDiffRenderer.renderCurrent("Hello world", "Hello brave world");
        assertThat(html).contains("<ins>brave </ins>");
        assertThat(html).contains("Hello");
        assertThat(html).contains("world");
    }

    @Test
    void rendersDeletionsInPrevious() {
        String html = PromptDiffRenderer.renderPrevious("Hello brave world", "Hello world");
        assertThat(html).contains("<del>brave </del>");
    }

    @Test
    void identicalPromptsProduceNoMarkup() {
        String current = PromptDiffRenderer.renderCurrent("same prompt", "same prompt");
        String previous = PromptDiffRenderer.renderPrevious("same prompt", "same prompt");
        assertThat(current).doesNotContain("<ins>").doesNotContain("<del>");
        assertThat(previous).doesNotContain("<ins>").doesNotContain("<del>");
    }

    @Test
    void escapesHtmlSpecialCharacters() {
        String html = PromptDiffRenderer.renderCurrent("a < b", "a < b & c");
        assertThat(html).contains("&lt;");
        assertThat(html).doesNotContain("a < b &");
    }

    @Test
    void handlesNullInputsGracefully() {
        assertThat(PromptDiffRenderer.renderCurrent(null, "hello")).contains("<ins>hello</ins>");
        assertThat(PromptDiffRenderer.renderPrevious("hello", null)).contains("<del>hello</del>");
    }
}
