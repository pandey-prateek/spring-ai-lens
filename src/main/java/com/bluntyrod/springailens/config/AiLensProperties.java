package com.bluntyrod.springailens.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Root configuration properties for spring-ai-lens, bound under the {@code ai-lens} prefix.
 *
 * <p>See {@code application.yaml} reference in the project README for the full property tree.
 */
@ConfigurationProperties(prefix = "ai-lens")
public class AiLensProperties {

    private int bufferSize = 500;
    private AnomalyProperties anomaly = new AnomalyProperties();
    private DashboardProperties dashboard = new DashboardProperties();
    private StorageProperties storage = new StorageProperties();
    private DiffProperties diff = new DiffProperties();

    public int getBufferSize() { return bufferSize; }
    public void setBufferSize(int bufferSize) { this.bufferSize = bufferSize; }

    public AnomalyProperties getAnomaly() { return anomaly; }
    public void setAnomaly(AnomalyProperties anomaly) { this.anomaly = anomaly; }

    public DashboardProperties getDashboard() { return dashboard; }
    public void setDashboard(DashboardProperties dashboard) { this.dashboard = dashboard; }

    public StorageProperties getStorage() { return storage; }
    public void setStorage(StorageProperties storage) { this.storage = storage; }

    public DiffProperties getDiff() { return diff; }
    public void setDiff(DiffProperties diff) { this.diff = diff; }
}
