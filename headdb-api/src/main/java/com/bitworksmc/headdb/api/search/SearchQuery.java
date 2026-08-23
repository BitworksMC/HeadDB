package com.bitworksmc.headdb.api.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** Structured catalog query shared by modern and legacy server artifacts. */
public final class SearchQuery {
    private final String name;
    private final String category;
    private final List<String> tags;
    private final List<Integer> ids;
    private final MatchMode matchMode;
    private final SearchSort sort;
    private final boolean ascending;
    private final int offset;
    private final int limit;

    private SearchQuery(Builder builder) {
        this.name = builder.name.trim();
        this.category = builder.category.trim();
        LinkedHashSet<String> normalizedTags = new LinkedHashSet<String>();
        for (String tag : builder.tags) {
            if (tag != null && !tag.trim().isEmpty()) normalizedTags.add(tag.trim());
        }
        LinkedHashSet<Integer> normalizedIds = new LinkedHashSet<Integer>();
        for (Integer id : builder.ids) {
            if (id != null && id.intValue() >= 0) normalizedIds.add(id);
        }
        this.tags = Collections.unmodifiableList(new ArrayList<String>(normalizedTags));
        this.ids = Collections.unmodifiableList(new ArrayList<Integer>(normalizedIds));
        this.matchMode = builder.matchMode;
        this.sort = builder.sort;
        this.ascending = builder.ascending;
        this.offset = Math.max(0, builder.offset);
        this.limit = Math.min(500, Math.max(1, builder.limit));
    }

    public static Builder builder() { return new Builder(); }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public List<String> getTags() { return tags; }
    public List<Integer> getIds() { return ids; }
    public MatchMode getMatchMode() { return matchMode; }
    public SearchSort getSort() { return sort; }
    public boolean isAscending() { return ascending; }
    public int getOffset() { return offset; }
    public int getLimit() { return limit; }

    public static final class Builder {
        private String name = "";
        private String category = "";
        private List<String> tags = Collections.emptyList();
        private List<Integer> ids = Collections.emptyList();
        private MatchMode matchMode = MatchMode.ALL;
        private SearchSort sort = SearchSort.ID;
        private boolean ascending = true;
        private int offset;
        private int limit = 100;

        public Builder name(String value) { this.name = value == null ? "" : value; return this; }
        public Builder category(String value) { this.category = value == null ? "" : value; return this; }
        public Builder tags(List<String> value) { this.tags = value == null ? Collections.<String>emptyList() : value; return this; }
        public Builder ids(List<Integer> value) { this.ids = value == null ? Collections.<Integer>emptyList() : value; return this; }
        public Builder matchMode(MatchMode value) { this.matchMode = value == null ? MatchMode.ALL : value; return this; }
        public Builder sort(SearchSort value) { this.sort = value == null ? SearchSort.ID : value; return this; }
        public Builder ascending(boolean value) { this.ascending = value; return this; }
        public Builder offset(int value) { this.offset = value; return this; }
        public Builder limit(int value) { this.limit = value; return this; }
        public SearchQuery build() { return new SearchQuery(this); }
    }
}
