package com.bitworksmc.headdb.core.menu;

import com.bitworksmc.headdb.core.HeadDB;
import com.bitworksmc.headdb.api.model.Head;
import com.bitworksmc.headdb.core.config.CustomCategory;
import com.bitworksmc.headdb.core.menu.gui.CustomCategoriesGUI;
import com.bitworksmc.headdb.core.menu.gui.HeadsGUI;
import com.bitworksmc.headdb.core.util.Compatibility;
import com.bitworksmc.headdb.core.util.PermissionUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class MenuManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MenuManager.class);
    private static final Set<String> RESERVED_CUSTOM_CATEGORY_IDS = Set.of("local", "favorites", "custom");
    private volatile MainMenu mainMenu;
    private volatile CustomCategoriesGUI customCategoriesGui;
    private volatile Map<String, HeadsGUI> guis = Map.of();
    private volatile List<String> categoryNames = List.of();

    public MenuManager(HeadDB plugin) {
        this.mainMenu = new MainMenu(plugin, List.of());
        this.customCategoriesGui = null;
    }

    public void registerDefaults(HeadDB plugin) {
        plugin.getHeadApi().onReady().thenAcceptAsync(
                heads -> registerDefaults(plugin, heads),
                Compatibility.getMainThreadExecutor(plugin)
        ).exceptionally(ex -> {
            LOGGER.error("Failed to register menus after the database became ready", ex);
            return null;
        });
    }

    public void registerDefaults(HeadDB plugin, List<Head> heads) {
        Map<String, List<Head>> headsByCategory = new LinkedHashMap<>();
        for (Head head : heads) {
            headsByCategory.computeIfAbsent(head.getCategory(), ignored -> new ArrayList<>()).add(head);
        }

        Map<String, HeadsGUI> updatedGuis = new HashMap<>();
        List<String> updatedCategoryNames = new ArrayList<>(headsByCategory.size());
        for (Map.Entry<String, List<Head>> entry : headsByCategory.entrySet()) {
            String knownCategory = entry.getKey();
            try {
                HeadsGUI gui = new HeadsGUI(
                        plugin,
                        knownCategory,
                        plugin.getLocalization().getConsoleMessage("menu.category." + knownCategory.toLowerCase(Locale.ROOT))
                                .orElseGet(() -> Component.text("HeadDB » " + knownCategory).color(NamedTextColor.GOLD)),
                        entry.getValue(),
                        knownCategory
                );
                updatedGuis.put(normalizeKey(knownCategory), gui);
                updatedCategoryNames.add(knownCategory);
            } catch (Throwable ex) {
                LOGGER.error("Failed to register known category: {}", knownCategory, ex);
            }
        }

        // Load custom categories
        List<CustomCategory> customCategories = new ArrayList<>();
        for (CustomCategory category : plugin.getCfg().resolveCustomCategories(heads)) {
            if (!category.isEnabled()) {
                LOGGER.debug("Skipping disabled custom category: {}", category.getIdentifier());
                continue;
            }
            String customKey = normalizeKey(category.getIdentifier());
            if (RESERVED_CUSTOM_CATEGORY_IDS.contains(customKey) || updatedGuis.containsKey(customKey)) {
                LOGGER.warn("Skipping custom category '{}' because its normalized ID '{}' is reserved or already in use.",
                        category.getIdentifier(), customKey);
                continue;
            }
            customCategories.add(category);
            updatedCategoryNames.add(category.getIdentifier());
            updatedGuis.put(customKey, new HeadsGUI(
                    plugin,
                    "custom_" + category.getIdentifier(),
                    plugin.getLocalization().getConsoleMessage("menu.category." + category.getIdentifier())
                            .orElseGet(() -> MiniMessage.miniMessage().deserialize("<red>HeadDB <gray>» " + category.getName())),
                    category.getHeads(),
                    category.getIdentifier()
            ));
        }

        CustomCategoriesGUI updatedCustomCategoriesGui = new CustomCategoriesGUI(
                plugin,
                "custom_categories",
                plugin.getLocalization().getConsoleMessage("menu.customCategories.name")
                        .orElseGet(() -> Component.text("HeadDB » More Categories").color(NamedTextColor.GOLD)),
                customCategories
        );
        MainMenu updatedMainMenu = new MainMenu(plugin, heads);

        // Publish the complete replacement only after every menu was constructed.
        this.guis = Map.copyOf(updatedGuis);
        this.customCategoriesGui = updatedCustomCategoriesGui;
        this.mainMenu = updatedMainMenu;
        this.categoryNames = List.copyOf(updatedCategoryNames);
    }

    public synchronized void register(String key, HeadsGUI menu) {
        Map<String, HeadsGUI> updated = new HashMap<>(this.guis);
        updated.put(normalizeKey(key), menu);
        this.guis = Map.copyOf(updated);
    }

    public HeadsGUI get(String key) {
        return key == null ? null : this.guis.get(normalizeKey(key));
    }

    public CustomCategoriesGUI getCustomCategoriesGui() {
        return customCategoriesGui;
    }

    public MainMenu getMainMenu() {
        return this.mainMenu;
    }

    public List<String> getCategoryNames() {
        return categoryNames;
    }

    private static String normalizeKey(String key) {
        return PermissionUtil.normalizeCategory(key);
    }

}
