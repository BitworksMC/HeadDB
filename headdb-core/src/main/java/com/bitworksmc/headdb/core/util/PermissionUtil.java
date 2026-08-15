package com.bitworksmc.headdb.core.util;

import org.bukkit.permissions.Permissible;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class PermissionUtil {

    private static final String CATEGORY_PERMISSION_PREFIX = "headdb.category.";
    private static final String CATEGORY_WILDCARD_PERMISSION = CATEGORY_PERMISSION_PREFIX + "*";
    private static final String CATEGORY_PARENT_PERMISSION = "headdb.category";
    private static final String LEGACY_FAVORITES_PERMISSION = "headdb.favorites";
    private static final Pattern INVALID_CATEGORY_CHARS = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_UNDERSCORES = Pattern.compile("^_+|_+$");

    private PermissionUtil() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean hasCategoryPermission(Permissible permissible, String category) {
        if (category == null) {
            return false;
        }

        String rawCategory = category.trim();
        if (rawCategory.isEmpty()) {
            return false;
        }

        Set<String> categoryNodes = new LinkedHashSet<>();

        // The documented, normalized node is authoritative when aliases conflict.
        String normalizedCategory = normalizeCategory(rawCategory);
        if (!normalizedCategory.isEmpty()) {
            categoryNodes.add(CATEGORY_PERMISSION_PREFIX + normalizedCategory);
        }

        String lowerCaseCategory = rawCategory.toLowerCase(Locale.ROOT);
        categoryNodes.add(CATEGORY_PERMISSION_PREFIX + lowerCaseCategory);
        categoryNodes.add(CATEGORY_PERMISSION_PREFIX + rawCategory);

        if ("favorites".equalsIgnoreCase(rawCategory)) {
            categoryNodes.add(LEGACY_FAVORITES_PERMISSION);
        }

        // A category-specific value is more specific than a wildcard. Checking
        // isPermissionSet is essential here: hasPermission alone cannot tell an
        // absent node from an explicit false value.
        for (String node : categoryNodes) {
            if (permissible.isPermissionSet(node)) {
                return permissible.hasPermission(node);
            }
        }

        // Preserve Bukkit permission defaults for category nodes when there is
        // no explicit override, then fall back to the two global aliases.
        for (String node : categoryNodes) {
            if (permissible.hasPermission(node)) {
                return true;
            }
        }

        if (permissible.isPermissionSet(CATEGORY_WILDCARD_PERMISSION)) {
            return permissible.hasPermission(CATEGORY_WILDCARD_PERMISSION);
        }
        if (permissible.isPermissionSet(CATEGORY_PARENT_PERMISSION)) {
            return permissible.hasPermission(CATEGORY_PARENT_PERMISSION);
        }

        return permissible.hasPermission(CATEGORY_WILDCARD_PERMISSION)
                || permissible.hasPermission(CATEGORY_PARENT_PERMISSION);
    }

    public static String normalizeCategory(String category) {
        if (category == null) {
            return "";
        }
        String normalized = INVALID_CATEGORY_CHARS
                .matcher(category.toLowerCase(Locale.ROOT))
                .replaceAll("_");
        return EDGE_UNDERSCORES.matcher(normalized).replaceAll("");
    }
}
