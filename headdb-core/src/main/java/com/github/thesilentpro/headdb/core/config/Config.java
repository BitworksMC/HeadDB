package com.github.thesilentpro.headdb.core.config;

import com.github.thesilentpro.headdb.api.model.Head;
import com.github.thesilentpro.headdb.core.HeadDB;
import com.github.thesilentpro.headdb.core.util.Compatibility;
import com.github.thesilentpro.headdb.implementation.Index;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

public class Config {

    private static final Logger LOGGER = LoggerFactory.getLogger(Config.class);

    // === Default Head Textures ===
    private static final String DEFAULT_DATABASE_SOURCE_URL = "https://raw.githubusercontent.com/BitworksMC/HeadDB/refs/heads/master/heads.json";
    private static final String DEFAULT_BACK_TEXTURE = "e5da4847272582265bdaca367237c96122b139f4e597fbc6667d3fb75fea7cf6";
    private static final String DEFAULT_INFO_TEXTURE = "93e5cb83cfdf42e9c4d8a3ecb4f889f6a5f418dce0a894c97e416a0eaf0d58";
    private static final String DEFAULT_NEXT_TEXTURE = "62bfb7ed2bd9f1d1f85c3d6ffb1626f252c5ecfd79d51a3f56ebf8e0c3c91";
    private static final String DEFAULT_CUSTOM_CATEGORY_TEXTURE = "62bfb7ed2bd9f1d1f85c3d6ffb1626f252c5ecfd79d51a3f56ebf8e0c3c91";
    private static final String DEFAULT_SEARCH_TEXTURE = "9d9cc58ad25a1ab16d36bb5d6d493c8f5898c2bf302b64e325921c41c35867";

    private final HeadDB plugin;
    private final FileConfiguration config;

    // General
    private long playerStorageSaveInterval;
    private int databaseThreads, apiThreads;
    private boolean preloadHeads, trackPage, updaterEnabled;
    private int maxBuyAmount;
    private List<Integer> omit;
    private List<String> databaseSourceUrls = List.of(DEFAULT_DATABASE_SOURCE_URL);

    // Indexing
    private boolean indexingEnabled, indexById, indexByTexture, indexByCategory, indexByTag;

    // Heads menu
    private boolean showInfoItem, headsMenuDividerEnabled;
    private int headsMenuRows, dividerRow;
    private Material headsMenuDividerMaterial;
    private String headsMenuDividerName;
    private final List<Component> headsLore = new ArrayList<>();
    private Component headName;

    // Control items (back, next, etc.)
    private String backTexture, infoTexture, nextTexture, customCategoryTexture, searchTexture;
    private Material backItem, infoItem, nextItem, customCategoryItem, searchItem;

    // Economy
    private String economyProvider;
    private final Map<String, Double> categoryPrices = new HashMap<>();
    private final Map<Integer, Double> headPrices = new HashMap<>();

    public Config(HeadDB plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    public void load() {
        loadGeneral();
        loadIndexing();
        loadEconomy();
        loadHeadsMenu();
        loadHeadsLore();
        loadControls();
    }

    private void loadGeneral() {
        playerStorageSaveInterval = config.getLong("storage.player.saveInterval", 1800L);
        updaterEnabled = config.getBoolean("updater", true);
        trackPage = config.getBoolean("trackPage", true);
        preloadHeads = config.getBoolean("preloadHeads", true);
        databaseThreads = config.getInt("database.threads", 1);
        apiThreads = config.getInt("database.apiThreads", 1);
        maxBuyAmount = config.getInt("maxBuyAmount", 2304);
        omit = config.getIntegerList("head.omit");
        loadDatabaseSources();

        LOGGER.trace("Loaded General Config:");
        LOGGER.trace(" - playerStorageSaveInterval = {}", playerStorageSaveInterval);
        LOGGER.trace(" - updaterEnabled = {}", updaterEnabled);
        LOGGER.trace(" - trackPage = {}", trackPage);
        LOGGER.trace(" - preloadHeads = {}", preloadHeads);
        LOGGER.trace(" - databaseThreads = {}", databaseThreads);
        LOGGER.trace(" - apiThreads = {}", apiThreads);
        LOGGER.trace(" - databaseSourceUrls = {}", databaseSourceUrls);
        LOGGER.trace(" - maxBuyAmount = {}", maxBuyAmount);
    }

    private void loadDatabaseSources() {
        String primarySource = config.getString("database.sourceUrl", DEFAULT_DATABASE_SOURCE_URL);
        List<String> fallbackSources = config.getStringList("database.fallbackSourceUrls");

        LinkedHashSet<String> orderedSources = new LinkedHashSet<>();

        if (primarySource != null) {
            String trimmed = primarySource.trim();
            if (!trimmed.isEmpty()) {
                orderedSources.add(trimmed);
            }
        }

        for (String source : fallbackSources) {
            if (source == null) {
                continue;
            }
            String trimmed = source.trim();
            if (!trimmed.isEmpty()) {
                orderedSources.add(trimmed);
            }
        }

        if (orderedSources.isEmpty()) {
            orderedSources.add(DEFAULT_DATABASE_SOURCE_URL);
        }

        databaseSourceUrls = List.copyOf(orderedSources);
    }

    private void loadIndexing() {
        indexingEnabled = config.getBoolean("indexing.enabled", true);
        indexById = config.getBoolean("indexing.by.id", true);
        indexByCategory = config.getBoolean("indexing.by.category", true);
        indexByTexture = config.getBoolean("indexing.by.texture", true);
        indexByTag = config.getBoolean("indexing.by.tag", true);

        LOGGER.trace("Loaded Indexing Config:");
        LOGGER.trace(" - indexingEnabled = {}", indexingEnabled);
        LOGGER.trace(" - indexById = {}", indexById);
        LOGGER.trace(" - indexByCategory = {}", indexByCategory);
        LOGGER.trace(" - indexByTexture = {}", indexByTexture);
        LOGGER.trace(" - indexByTag = {}", indexByTag);
    }

    private void loadHeadsMenu() {
        showInfoItem = config.getBoolean("showInfoItem", true);
        headsMenuDividerEnabled = config.getBoolean("headsMenu.divider.enabled", true);
        headsMenuRows = config.getInt("headsMenu.rows", 4);
        dividerRow = config.getInt("headsMenu.divider.row", 5);

        headsMenuDividerMaterial = parseMaterial("headsMenu.divider.item.material", "BLACK_STAINED_GLASS_PANE");
        headsMenuDividerName = config.getString("headsMenu.divider.item.name", " ");

        LOGGER.trace("Loaded Heads Menu Config:");
        LOGGER.trace(" - showInfoItem = {}", showInfoItem);
        LOGGER.trace(" - headsMenuDividerEnabled = {}", headsMenuDividerEnabled);
        LOGGER.trace(" - headsMenuRows = {}", headsMenuRows);
        LOGGER.trace(" - dividerRow = {}", dividerRow);
        LOGGER.trace(" - headsMenuDividerMaterial = {}", headsMenuDividerMaterial);
        LOGGER.trace(" - headsMenuDividerName = '{}'", headsMenuDividerName);
    }

    private void loadHeadsLore() {
        List<String> lore;
        if (plugin.getCfg().getEconomyProvider() == null || plugin.getCfg().getEconomyProvider().isBlank()) {
            headName = MiniMessage.miniMessage().deserialize(config.getString("head.name.default", "{name}"));
            lore = config.getStringList("head.lore.default");
        } else {
            headName = MiniMessage.miniMessage().deserialize(config.getString("head.name.economy", "{name}"));
            lore = config.getStringList("head.lore.economy");
        }

        LOGGER.trace("Loaded heads lore template ({}):", plugin.getEconomyProvider() == null ? "no-econ" : "econ");
        for (String line : lore) {
            this.headsLore.add(MiniMessage.miniMessage().deserialize(line));
            LOGGER.trace(line);
        };
    }

    private void loadControls() {
        backTexture = config.getString("controls.back.head", DEFAULT_BACK_TEXTURE);
        backItem = parseMaterial("controls.back.item", "ARROW");

        infoTexture = config.getString("controls.info.head", DEFAULT_INFO_TEXTURE);
        infoItem = parseMaterial("controls.info.item", "PAPER");

        nextTexture = config.getString("controls.next.head", DEFAULT_NEXT_TEXTURE);
        nextItem = parseMaterial("controls.next.item", "ARROW");

        customCategoryTexture = config.getString("headsMenu.customCategories.head", DEFAULT_CUSTOM_CATEGORY_TEXTURE);
        customCategoryItem = parseMaterial("headsMenu.customCategories.item", "BOOKSHELF");

        searchTexture = config.getString("headsMenu.search.head", DEFAULT_SEARCH_TEXTURE);
        searchItem = parseMaterial("headsMenu.search.item", "SPYGLASS");

        LOGGER.trace("Loaded Controls Config:");
        LOGGER.trace(" - backTexture = {}", backTexture);
        LOGGER.trace(" - backItem = {}", backItem);
        LOGGER.trace(" - infoTexture = {}", infoTexture);
        LOGGER.trace(" - infoItem = {}", infoItem);
        LOGGER.trace(" - nextTexture = {}", nextTexture);
        LOGGER.trace(" - nextItem = {}", nextItem);
        LOGGER.trace(" - customCategoryTexture = {}", customCategoryTexture);
        LOGGER.trace(" - customCategoryItem = {}", customCategoryItem);
        LOGGER.trace(" - searchTexture = {}", searchTexture);
        LOGGER.trace(" - searchItem = {}", searchItem);
    }

    private void loadEconomy() {
        economyProvider = config.getString("economy.provider", null);
        categoryPrices.clear();
        if (!economyProvider.isEmpty() && !economyProvider.equalsIgnoreCase("NONE")) {
            ConfigurationSection section = config.getConfigurationSection("economy.cost.category");
            if (section != null) {
                for (String category : section.getKeys(false)) {
                    double price = section.getDouble(category, 0D);
                    categoryPrices.put(category, price);
                    LOGGER.trace("Loaded price: category='{}' price={}", category, price);
                }
            }
            ConfigurationSection headSection = config.getConfigurationSection("economy.cost.head");
            if (headSection != null) {
                for (String headId : headSection.getKeys(false)) {
                    try {
                        double price = headSection.getDouble(headId, 0D);
                        headPrices.put(Integer.parseInt(headId), price);
                        LOGGER.trace("Loaded price: head='{}' price='{}'", headId, price);
                    } catch (NumberFormatException nfe) {
                        LOGGER.error("Invalid head id '{}' in config", headId);
                    }
                }
            }
        }
        LOGGER.trace("Loaded Economy Config:");
        LOGGER.trace(" - economyProvider = '{}'", economyProvider);
    }

    private Material parseMaterial(String path, String fallback) {
        Material mat = Optional.ofNullable(Material.matchMaterial(config.getString(path, fallback))).orElse(Material.BARRIER);
        LOGGER.trace("Parsed material for '{}': {}", path, mat);
        return mat;
    }

    public List<CustomCategory> resolveCustomCategories() {
        List<CustomCategory> categories = new ArrayList<>();
        File file = new File(plugin.getDataFolder(), "categories.yml");

        if (!file.exists()) {
            plugin.saveResource("categories.yml", false);
        }

        if (!file.exists()) {
            LOGGER.error("Could not find or create categories.yml!");
            return categories;
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        for (String id : cfg.getKeys(false)) {
            CustomCategory category = parseCategory(cfg, id);
            if (category != null) {
                categories.add(category);
            }
        }

        return categories;
    }

    private @Nullable CustomCategory parseCategory(FileConfiguration cfg, String id) {
        String base = id + ".icon";
        String iconName = cfg.getString(base + ".name");
        if (iconName == null) {
            LOGGER.error("Missing icon name for category '{}'", id);
            return null;
        }

        ItemStack icon = null;
        String head = cfg.getString(base + ".head");

        if (head != null && !head.isEmpty()) {
            Optional<Head> headOpt = plugin.getHeadApi().findByTexture(head).join();
            if (headOpt.isPresent()) {
                icon = Compatibility.setItemDetails(headOpt.get().getItem(),
                        MiniMessage.miniMessage().deserialize(iconName), Component.empty());
            } else {
                LOGGER.warn("Head not found for category '{}'", id);
            }
        }

        if (icon == null) {
            String item = cfg.getString(base + ".item");
            if (item != null) {
                Material mat = Material.matchMaterial(item);
                if (mat != null) {
                    icon = Compatibility.newItem(mat, MiniMessage.miniMessage().deserialize(iconName), Component.empty());
                } else {
                    LOGGER.error("Unknown material '{}' for category '{}'", item, id);
                }
            }
        }

        if (icon == null) {
            LOGGER.error("No valid icon for category '{}'", id);
            return null;
        }

        List<Head> heads = new ArrayList<>();
        for (String texture : cfg.getStringList(id + ".heads")) {
            plugin.getHeadApi().findByTexture(texture).join().ifPresent(heads::add);
        }

        boolean enabled = cfg.getBoolean(id + ".enabled", false);
        return new CustomCategory(id, enabled, iconName, icon, heads);
    }

    public @Nullable Index[] resolveEnabledIndexes() {
        if (!indexingEnabled) return null;

        List<Index> indexes = new ArrayList<>();
        if (indexById) indexes.add(Index.ID);
        if (indexByCategory) indexes.add(Index.CATEGORY);
        if (indexByTexture) indexes.add(Index.TEXTURE);
        if (indexByTag) indexes.add(Index.TAG);

        return indexes.isEmpty() ? null : indexes.toArray(new Index[0]);
    }

    // === Getters ===

    public long getPlayerStorageSaveInterval() { return playerStorageSaveInterval; }
    public boolean isShowInfoItem() { return showInfoItem; }
    public boolean isHeadsMenuDividerEnabled() { return headsMenuDividerEnabled; }
    public int getHeadsMenuRows() { return headsMenuRows; }
    public int getDividerRow() { return dividerRow; }
    public Material getHeadsMenuDividerMaterial() { return headsMenuDividerMaterial; }
    public String getHeadsMenuDividerName() { return headsMenuDividerName; }
    public List<Component> getHeadsLore() {return headsLore; }
    public Component getHeadName() { return headName; }

    public boolean isUpdaterEnabled() { return updaterEnabled; }
    public int getDatabaseThreads() { return databaseThreads; }
    public int getApiThreads() { return apiThreads; }
    public List<String> getDatabaseSourceUrls() { return databaseSourceUrls; }
    public int getMaxBuyAmount() { return maxBuyAmount; }
    public List<Integer> getOmit() { return omit; }

    public boolean isTrackPage() { return trackPage; }
    public boolean isPreloadHeads() { return preloadHeads; }

    public boolean isIndexingEnabled() { return indexingEnabled; }
    public boolean isIndexByCategory() { return indexByCategory; }
    public boolean isIndexById() { return indexById; }
    public boolean isIndexByTag() { return indexByTag; }
    public boolean isIndexByTexture() { return indexByTexture; }

    public String getBackTexture() { return backTexture; }
    public Material getBackItem() { return backItem; }
    public String getInfoTexture() { return infoTexture; }
    public Material getInfoItem() { return infoItem; }
    public String getNextTexture() { return nextTexture; }
    public Material getNextItem() { return nextItem; }
    public String getCustomCategoryTexture() { return customCategoryTexture; }
    public Material getCustomCategoryItem() { return customCategoryItem; }
    public String getSearchTexture() { return searchTexture; }
    public Material getSearchItem() { return searchItem; }

    public @Nullable String getEconomyProvider() { return economyProvider; }
    public double getCategoryPrice(String id) { return categoryPrices.getOrDefault(id, 0D); }
    public double getHeadPrice(int id) { return headPrices.getOrDefault(id, -1D); }
    public Map<String, Double> getCategoryPrices() { return categoryPrices; }
    public double getHeadOrCategoryPrice(int id, String category) {
        double headPrice = getHeadPrice(id);
        return headPrice != -1 ? headPrice : getCategoryPrice(category);
    }

}
