package com.bluntyrod.springailens.config;

/**
 * Configuration for prompt diff tracking and regression alerting.
 */
public class DiffProperties {

    /** Whether a warning should be logged when a prompt template changes. Default {@code true}. */
    private boolean alertOnChange = true;

    /** Optional webhook URL that receives an HTTP POST with a JSON payload whenever a prompt changes. */
    private String webhookUrl;

    public boolean isAlertOnChange() { return alertOnChange; }
    public void setAlertOnChange(boolean alertOnChange) { this.alertOnChange = alertOnChange; }

    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
}
