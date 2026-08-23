package com.bitworksmc.headdb.api.search;

import com.bitworksmc.headdb.api.model.Head;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Platform-neutral implementation of HeadDB's structured search contract. */
public final class HeadSearch {
    private HeadSearch() { }

    public static SearchPage search(List<Head> heads, SearchQuery query) {
        List<Head> matches = new ArrayList<Head>();
        for (Head head : heads) {
            if (matches(head, query)) matches.add(head);
        }

        Comparator<Head> comparator;
        switch (query.getSort()) {
            case NAME:
                comparator = Comparator.comparing(Head::getName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(Head::getId);
                break;
            case CATEGORY:
                comparator = Comparator.comparing(Head::getCategory, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(Head::getId);
                break;
            case ID:
            default:
                comparator = Comparator.comparingInt(Head::getId);
                break;
        }
        if (!query.isAscending()) comparator = comparator.reversed();
        matches.sort(comparator);

        int total = matches.size();
        int from = Math.min(query.getOffset(), total);
        int to = Math.min(from + query.getLimit(), total);
        return new SearchPage(matches.subList(from, to), total, query.getOffset(), query.getLimit());
    }

    private static boolean matches(Head head, SearchQuery query) {
        List<Boolean> dimensions = new ArrayList<Boolean>(4);
        if (!query.getName().isEmpty()) {
            dimensions.add(head.getName().toLowerCase(Locale.ROOT)
                    .contains(query.getName().toLowerCase(Locale.ROOT)));
        }
        if (!query.getCategory().isEmpty()) {
            dimensions.add(head.getCategory().equalsIgnoreCase(query.getCategory())
                    || slugify(head.getCategory()).equals(slugify(query.getCategory())));
        }
        if (!query.getIds().isEmpty()) dimensions.add(query.getIds().contains(head.getId()));
        if (!query.getTags().isEmpty()) {
            Set<String> headTags = new HashSet<String>();
            for (String tag : head.getTags()) headTags.add(tag.toLowerCase(Locale.ROOT));
            boolean tagMatch = query.getMatchMode() == MatchMode.ANY;
            if (query.getMatchMode() == MatchMode.ALL) {
                tagMatch = true;
                for (String tag : query.getTags()) {
                    if (!headTags.contains(tag.toLowerCase(Locale.ROOT))) { tagMatch = false; break; }
                }
            } else {
                tagMatch = false;
                for (String tag : query.getTags()) {
                    if (headTags.contains(tag.toLowerCase(Locale.ROOT))) { tagMatch = true; break; }
                }
            }
            dimensions.add(tagMatch);
        }
        if (dimensions.isEmpty()) return true;
        if (query.getMatchMode() == MatchMode.ANY) {
            for (Boolean value : dimensions) if (value) return true;
            return false;
        }
        for (Boolean value : dimensions) if (!value) return false;
        return true;
    }

    private static String slugify(String value) {
        return value.trim().toLowerCase(Locale.ROOT)
                .replace("&", " and ")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}
