package com.bitworksmc.headdb.api.search;

import com.bitworksmc.headdb.api.model.Head;
import com.bitworksmc.headdb.implementation.model.BaseHead;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeadSearchTest {

    private final List<Head> heads = Arrays.<Head>asList(
            new BaseHead(3, "Cherry Cake", "three", "Food & Drinks", Arrays.asList("Dessert", "Pink")),
            new BaseHead(1, "Oak Crate", "one", "Decoration", Arrays.asList("Wood")),
            new BaseHead(2, "Dragon Egg", "two", "Blocks", Arrays.asList("Dragon", "Dark"))
    );

    @Test
    void supportsAllFiltersPaginationAndCategorySlugs() {
        SearchQuery query = SearchQuery.builder()
                .category("food-and-drinks")
                .tags(Arrays.asList("Dessert", null, "Dessert"))
                .matchMode(MatchMode.ALL)
                .limit(1)
                .build();

        SearchPage page = HeadSearch.search(heads, query);
        assertEquals(1, page.getTotal());
        assertEquals(3, page.getItems().get(0).getId());
        assertTrue(!page.hasMore());
    }

    @Test
    void supportsAnyDimensionsAndStableDescendingSort() {
        SearchQuery query = SearchQuery.builder()
                .name("crate")
                .ids(Arrays.asList(2))
                .matchMode(MatchMode.ANY)
                .sort(SearchSort.ID)
                .ascending(false)
                .build();

        SearchPage page = HeadSearch.search(heads, query);
        assertEquals(Arrays.asList(2, 1), Arrays.asList(
                page.getItems().get(0).getId(), page.getItems().get(1).getId()));
    }
}
