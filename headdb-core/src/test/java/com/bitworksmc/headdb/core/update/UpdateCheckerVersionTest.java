package com.bitworksmc.headdb.core.update;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.bitworksmc.headdb.core.update.UpdateChecker.VersionComparison.CURRENT_AHEAD;
import static com.bitworksmc.headdb.core.update.UpdateChecker.VersionComparison.INVALID;
import static com.bitworksmc.headdb.core.update.UpdateChecker.VersionComparison.UPDATE_AVAILABLE;
import static com.bitworksmc.headdb.core.update.UpdateChecker.VersionComparison.UP_TO_DATE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdateCheckerVersionTest {

    @Test
    void onlyReportsVersionsThatAreActuallyNewer() {
        assertEquals(UPDATE_AVAILABLE, UpdateChecker.compareVersions("6.0.2", "v6.0.3"));
        assertEquals(UPDATE_AVAILABLE, UpdateChecker.compareVersions("6.0.2-SNAPSHOT", "v6.0.2"));
        assertEquals(UP_TO_DATE, UpdateChecker.compareVersions("6.0.2", "v6.0.2"));
        assertEquals(CURRENT_AHEAD, UpdateChecker.compareVersions("6.1.0", "v6.0.3"));
        assertEquals(CURRENT_AHEAD, UpdateChecker.compareVersions("6.1.0-RC.1", "v6.0.3"));
    }

    @Test
    void failsClosedForInvalidLocalOrRemoteVersions() {
        assertEquals(INVALID, UpdateChecker.compareVersions("development", "v6.0.3"));
        assertEquals(INVALID, UpdateChecker.compareVersions("6.0.2", "latest"));
    }

    @Test
    void exponentiallyBacksOffRateLimitsWithoutServerGuidance() {
        assertEquals(Duration.ofHours(1), UpdateChecker.fallbackRateLimitDelay(0));
        assertEquals(Duration.ofHours(2), UpdateChecker.fallbackRateLimitDelay(1));
        assertEquals(Duration.ofHours(16), UpdateChecker.fallbackRateLimitDelay(4));
        assertEquals(Duration.ofHours(24), UpdateChecker.fallbackRateLimitDelay(5));
        assertEquals(Duration.ofHours(24), UpdateChecker.fallbackRateLimitDelay(Integer.MAX_VALUE));
    }
}
