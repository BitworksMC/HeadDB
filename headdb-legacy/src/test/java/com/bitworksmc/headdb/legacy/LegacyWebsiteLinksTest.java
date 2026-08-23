package com.bitworksmc.headdb.legacy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LegacyWebsiteLinksTest {

    @Test
    public void carriesSupportedSearchFiltersIntoWebsiteUrl() {
        assertEquals(
                "https://headdb.net/heads?q=dark+oak&category=food-and-drinks&tags=dark%2Cwood",
                LegacyWebsiteLinks.searchUrl("https://headdb.net/", new String[]{
                        "search", "dark", "oak", "category:Food & Drinks", "tags:dark,wood"
                })
        );
    }

    @Test
    public void buildsSubmissionUrlFromValidatedBaseUrl() {
        assertEquals("https://headdb.net/submit", LegacyWebsiteLinks.submissionUrl("javascript:alert(1)"));
    }

    @Test
    public void preservesMultipleIdsAndAnyMatchMode() {
        assertEquals(
                "https://headdb.net/heads?ids=12%2C34&match=any",
                LegacyWebsiteLinks.searchUrl("https://headdb.net", new String[]{
                        "search", "ids:12,34", "--any"
                })
        );
    }

    @Test
    public void combinesQuotedCategoryValues() {
        assertEquals(
                "https://headdb.net/heads?category=food-and-drinks",
                LegacyWebsiteLinks.searchUrl("https://headdb.net", new String[]{
                        "search", "category:\"Food", "&", "Drinks\""
                })
        );
    }
}
