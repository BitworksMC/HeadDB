package com.bitworksmc.headdb.api.search;

import com.bitworksmc.headdb.api.model.Head;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SearchPage {
    private final List<Head> items;
    private final int total;
    private final int offset;
    private final int limit;

    public SearchPage(List<Head> items, int total, int offset, int limit) {
        this.items = Collections.unmodifiableList(new ArrayList<Head>(items));
        this.total = total;
        this.offset = offset;
        this.limit = limit;
    }

    public List<Head> getItems() { return items; }
    public int getTotal() { return total; }
    public int getOffset() { return offset; }
    public int getLimit() { return limit; }
    public boolean hasMore() { return offset + items.size() < total; }
}
