package com.bitworksmc.headdb.core.util;

import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.permissions.Permissible;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionUtilTest {

    @Test
    void wildcardCanExcludeLocalHeads() {
        Permissible permissible = permissions(
                "headdb.category.*", true,
                "headdb.category.local", false
        );

        assertFalse(PermissionUtil.hasCategoryPermission(permissible, "local"));
        assertTrue(PermissionUtil.hasCategoryPermission(permissible, "Animals"));
    }

    @Test
    void parentPermissionCanExcludeLocalHeads() {
        Permissible permissible = permissions(
                "headdb.category", true,
                "headdb.category.local", false
        );

        assertFalse(PermissionUtil.hasCategoryPermission(permissible, "local"));
        assertTrue(PermissionUtil.hasCategoryPermission(permissible, "Animals"));
    }

    @Test
    void normalizedCategoryDenialOverridesWildcard() {
        Permissible permissible = permissions(
                "headdb.category.*", true,
                "headdb.category.food_drinks", false
        );

        assertFalse(PermissionUtil.hasCategoryPermission(permissible, "Food & Drinks"));
    }

    @Test
    void specificGrantOverridesWildcardDenial() {
        Permissible permissible = permissions(
                "headdb.category.*", false,
                "headdb.category.local", true
        );

        assertTrue(PermissionUtil.hasCategoryPermission(permissible, "local"));
        assertFalse(PermissionUtil.hasCategoryPermission(permissible, "Animals"));
    }

    @Test
    void normalizedNodeTakesPrecedenceOverCompatibilityAlias() {
        Permissible permissible = permissions(
                "headdb.category.food_drinks", false,
                "headdb.category.food & drinks", true
        );

        assertFalse(PermissionUtil.hasCategoryPermission(permissible, "Food & Drinks"));
    }

    @Test
    void legacyFavoritesDenialOverridesWildcard() {
        Permissible permissible = permissions(
                "headdb.category.*", true,
                "headdb.favorites", false
        );

        assertFalse(PermissionUtil.hasCategoryPermission(permissible, "favorites"));
    }

    @Test
    void wildcardValueTakesPrecedenceOverParentAlias() {
        Permissible permissible = permissions(
                "headdb.category", true,
                "headdb.category.*", false
        );

        assertFalse(PermissionUtil.hasCategoryPermission(permissible, "Animals"));
    }

    @Test
    void nullAndBlankCategoriesAreRejected() {
        Permissible permissible = permissions("headdb.category.*", true);

        assertFalse(PermissionUtil.hasCategoryPermission(permissible, null));
        assertFalse(PermissionUtil.hasCategoryPermission(permissible, "  "));
    }

    @Test
    void categoryNormalizationIsSharedByPermissionsAndMenus() {
        assertEquals("food_drinks", PermissionUtil.normalizeCategory(" Food & Drinks "));
        assertEquals("foo_bar", PermissionUtil.normalizeCategory("foo---bar"));
        assertEquals("", PermissionUtil.normalizeCategory(null));
    }

    private static Permissible permissions(Object... entries) {
        Map<String, Boolean> values = new HashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            values.put(((String) entries[i]).toLowerCase(Locale.ROOT), (Boolean) entries[i + 1]);
        }
        return new TestPermissible(values);
    }

    private record TestPermissible(Map<String, Boolean> permissions) implements Permissible {

        @Override
        public boolean isPermissionSet(String name) {
            return permissions.containsKey(name.toLowerCase(Locale.ROOT));
        }

        @Override
        public boolean isPermissionSet(Permission permission) {
            return isPermissionSet(permission.getName());
        }

        @Override
        public boolean hasPermission(String name) {
            return permissions.getOrDefault(name.toLowerCase(Locale.ROOT), false);
        }

        @Override
        public boolean hasPermission(Permission permission) {
            return hasPermission(permission.getName());
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeAttachment(PermissionAttachment attachment) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recalculatePermissions() {
        }

        @Override
        public Set<PermissionAttachmentInfo> getEffectivePermissions() {
            return Set.of();
        }

        @Override
        public boolean isOp() {
            return false;
        }

        @Override
        public void setOp(boolean value) {
        }
    }
}
