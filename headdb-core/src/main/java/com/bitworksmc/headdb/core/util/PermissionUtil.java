package com.bitworksmc.headdb.core.util;

import org.bukkit.permissions.Permissible;

import java.util.Locale;
import java.util.regex.Pattern;

public final class PermissionUtil {

    private static final Pattern INVALID_CATEGORY_CHARS = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_UNDERSCORES = Pattern.compile("^_+|_+$");

    private PermissionUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean hasCategoryPermission(Permissible permissible, String category) {
        if (permissible.hasPermission("headdb.category.*") || permissible.hasPermission("headdb.category")) {
            return true;
        }

        if (category == null) {
            return false;
        }

        String rawCategory = category.trim();
        if (rawCategory.isEmpty()) {
            return false;
        }

        String rawNode = "headdb.category." + rawCategory;
        if (permissible.hasPermission(rawNode)) {
            return true;
        }

        String lowerCaseCategory = rawCategory.toLowerCase(Locale.ROOT);
        String lowerCaseNode = "headdb.category." + lowerCaseCategory;
        if (!lowerCaseNode.equals(rawNode) && permissible.hasPermission(lowerCaseNode)) {
            return true;
        }

        String normalizedCategory = normalizeCategory(rawCategory);
        if (!normalizedCategory.isEmpty()) {
            String normalizedNode = "headdb.category." + normalizedCategory;
            if (!normalizedNode.equals(lowerCaseNode) && permissible.hasPermission(normalizedNode)) {
                return true;
            }
        }

        return "favorites".equalsIgnoreCase(rawCategory) && permissible.hasPermission("headdb.favorites");
    }

    private static String normalizeCategory(String category) {
        String normalized = INVALID_CATEGORY_CHARS
                .matcher(category.toLowerCase(Locale.ROOT))
                .replaceAll("_");
        return EDGE_UNDERSCORES.matcher(normalized).replaceAll("");
    }
}
