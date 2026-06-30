package com.bluntyrod.springailens.util.diff;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.bluntyrod.springailens.config.DiffProperties;
import com.bluntyrod.springailens.model.AiCallEvent;
import com.bluntyrod.springailens.model.AnomalyReport;
import com.bluntyrod.springailens.model.PromptDiffResult;

class PromptRegressionAlerterTest {

    private AiCallEvent eventWithDiff(PromptDiffResult diff) {
        return new AiCallEvent(UUID.randomUUID().toString(), Instant.now(), "OpenAiChatModel",
                "current prompt", "response", 10, 5, 5, AnomalyReport.none(), diff);
    }

    @Test
    void doesNothingWhenDiffIsNull() {
        PromptRegressionAlerter alerter = new PromptRegressionAlerter(new DiffProperties());
        assertThatCode(() -> alerter.onEvent(eventWithDiff(null))).doesNotThrowAnyException();
    }

    @Test
    void doesNothingWhenPromptUnchanged() {
        PromptRegressionAlerter alerter = new PromptRegressionAlerter(new DiffProperties());
        assertThatCode(() -> alerter.onEvent(eventWithDiff(PromptDiffResult.unchanged("hash")))).doesNotThrowAnyException();
    }

    @Test
    void logsWarningOnChangeWithoutWebhook() {
        DiffProperties properties = new DiffProperties();
        properties.setWebhookUrl(null);
        PromptRegressionAlerter alerter = new PromptRegressionAlerter(properties);

        PromptDiffResult changed = PromptDiffResult.changed("oldHash", "newHash", "old prompt", "current prompt");
        assertThatCode(() -> alerter.onEvent(eventWithDiff(changed))).doesNotThrowAnyException();
    }

    @Test
    void doesNotThrowWhenWebhookUrlIsInvalid() {
        DiffProperties properties = new DiffProperties();
        properties.setWebhookUrl("not-a-valid-url");
        PromptRegressionAlerter alerter = new PromptRegressionAlerter(properties);

        PromptDiffResult changed = PromptDiffResult.changed("oldHash", "newHash", "old prompt", "current prompt");
        assertThatCode(() -> alerter.onEvent(eventWithDiff(changed))).doesNotThrowAnyException();
    }
}
