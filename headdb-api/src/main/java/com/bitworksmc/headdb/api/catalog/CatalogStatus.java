package com.bitworksmc.headdb.api.catalog;

/** Immutable operational state for the locally synchronized HeadDB catalog. */
public final class CatalogStatus {
    private final boolean ready;
    private final int revision;
    private final int headCount;
    private final long lastAttemptEpochMillis;
    private final long lastSuccessfulUpdateEpochMillis;
    private final String lastError;
    private final String source;

    public CatalogStatus(boolean ready, int revision, int headCount, long lastAttemptEpochMillis,
                         long lastSuccessfulUpdateEpochMillis, String lastError, String source) {
        this.ready = ready;
        this.revision = revision;
        this.headCount = headCount;
        this.lastAttemptEpochMillis = lastAttemptEpochMillis;
        this.lastSuccessfulUpdateEpochMillis = lastSuccessfulUpdateEpochMillis;
        this.lastError = lastError;
        this.source = source;
    }

    public boolean isReady() { return ready; }
    public int getRevision() { return revision; }
    public int getHeadCount() { return headCount; }
    public long getLastAttemptEpochMillis() { return lastAttemptEpochMillis; }
    public long getLastSuccessfulUpdateEpochMillis() { return lastSuccessfulUpdateEpochMillis; }
    public String getLastError() { return lastError; }
    public String getSource() { return source; }
}
