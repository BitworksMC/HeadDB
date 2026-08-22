package com.bitworksmc.headdb.core.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebsiteLinksTest {

    @Test
    void buildsSearchUrlFromSupportedPluginFilters() {
        assertEquals(
                "https://headdb.net/heads?q=dark+oak&category=food-and-drinks&tags=dark%2Cwood",
                WebsiteLinks.searchUrl(
                        "https://headdb.net/",
                        "dark oak",
                        "Food & Drinks",
                        List.of("Dark", "Wood"),
                        List.of()
                )
        );
    }

    @Test
    void usesSingleIdAsWebsiteQueryWhenNoNameWasProvided() {
        assertEquals(
                "https://headdb.net/heads?q=103838",
                WebsiteLinks.searchUrl("https://headdb.net", "", null, List.of(), List.of(103838))
        );
    }

    @Test
    void rejectsInvalidConfiguredBaseUrl() {
        assertEquals("https://headdb.net/submit", WebsiteLinks.submissionUrl("javascript:alert(1)"));
    }
}
