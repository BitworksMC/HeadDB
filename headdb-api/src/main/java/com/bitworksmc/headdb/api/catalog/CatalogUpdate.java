package com.bitworksmc.headdb.api.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A successfully published local catalog transition. */
public final class CatalogUpdate {
    private final int previousRevision;
    private final int revision;
    private final List<Integer> addedIds;
    private final List<Integer> updatedIds;
    private final List<Integer> removedIds;
    private final long completedAtEpochMillis;

    public CatalogUpdate(int previousRevision, int revision, List<Integer> addedIds,
                         List<Integer> updatedIds, List<Integer> removedIds, long completedAtEpochMillis) {
        this.previousRevision = previousRevision;
        this.revision = revision;
        this.addedIds = immutableCopy(addedIds);
        this.updatedIds = immutableCopy(updatedIds);
        this.removedIds = immutableCopy(removedIds);
        this.completedAtEpochMillis = completedAtEpochMillis;
    }

    public int getPreviousRevision() { return previousRevision; }
    public int getRevision() { return revision; }
    public List<Integer> getAddedIds() { return addedIds; }
    public List<Integer> getUpdatedIds() { return updatedIds; }
    public List<Integer> getRemovedIds() { return removedIds; }
    public long getCompletedAtEpochMillis() { return completedAtEpochMillis; }
    public boolean hasChanges() { return !addedIds.isEmpty() || !updatedIds.isEmpty() || !removedIds.isEmpty(); }

    private static List<Integer> immutableCopy(List<Integer> values) {
        return Collections.unmodifiableList(new ArrayList<Integer>(values));
    }
}
