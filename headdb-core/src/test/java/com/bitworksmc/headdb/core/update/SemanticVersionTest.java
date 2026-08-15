package com.bitworksmc.headdb.core.update;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticVersionTest {

    @Test
    void acceptsVersionTagsAndIgnoresBuildMetadata() {
        assertComparison(" v6.0.2 ", "6.0.2", 0);
        assertComparison("V6.0.2+paper.112", "6.0.2+spigot.4646", 0);
    }

    @Test
    void comparesNumericComponentsWithoutLexicographicMistakes() {
        assertComparison("6.0.10", "6.0.9", 1);
        assertComparison("7.0.0", "6.99.99", 1);
        assertComparison("6.1.0", "6.0.999", 1);
        assertComparison("999999999999999999999.0.0", "9.999.999", 1);
    }

    @Test
    void appliesSemanticVersionPrereleasePrecedence() {
        List<String> ordered = List.of(
                "1.0.0-alpha",
                "1.0.0-alpha.1",
                "1.0.0-alpha.beta",
                "1.0.0-beta",
                "1.0.0-beta.2",
                "1.0.0-beta.11",
                "1.0.0-rc.1",
                "1.0.0"
        );

        for (int index = 1; index < ordered.size(); index++) {
            assertComparison(ordered.get(index), ordered.get(index - 1), 1);
        }
        assertComparison("6.0.2", "6.0.2-SNAPSHOT", 1);
        assertComparison("6.1.0-SNAPSHOT", "6.0.2", 1);
    }

    @Test
    void rejectsMalformedVersions() {
        for (String invalid : new String[]{
                null, "", "v", "6", "6.0", "6.0.2.1", "01.0.0", "1..0",
                "1.0.0-", "1.0.0-alpha..1", "1.0.0-01", "release-6.0.2", "1.0.0+"
        }) {
            assertTrue(SemanticVersion.parse(invalid).isEmpty(), () -> "Expected invalid version: " + invalid);
        }
    }

    private static void assertComparison(String left, String right, int expectedSign) {
        SemanticVersion leftVersion = SemanticVersion.parse(left).orElseThrow();
        SemanticVersion rightVersion = SemanticVersion.parse(right).orElseThrow();
        assertEquals(expectedSign, Integer.signum(leftVersion.compareTo(rightVersion)));
    }
}
