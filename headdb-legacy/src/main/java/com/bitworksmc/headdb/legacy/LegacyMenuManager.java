package com.bitworksmc.headdb.legacy;

import com.bitworksmc.headdb.api.model.Head;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class LegacyMenuManager implements Listener {
    private static final int PAGE_SIZE = 45;
    private final LegacyHeadDB plugin;
    private final LegacyDatabase database;
    private final LegacyHeadAPI api;
    private final LegacyPlayerStorage storage;
    private final LegacyMessages messages;
    private final LegacySounds sounds;
    private final LegacyEconomy economy;

    LegacyMenuManager(LegacyHeadDB plugin, LegacyDatabase database, LegacyHeadAPI api,
                      LegacyPlayerStorage storage, LegacyMessages messages, LegacySounds sounds,
                      LegacyEconomy economy) {
        this.plugin = plugin;
        this.database = database;
        this.api = api;
        this.storage = storage;
        this.messages = messages;
        this.sounds = sounds;
        this.economy = economy;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    void openMain(Player player) {
        MenuHolder holder = new MenuHolder("main", "", 0, Collections.<Head>emptyList());
        Inventory inventory = create(holder, 54, messages.get("menu.main.name", "HeadDB"));
        int slot = 0;
        for (String category : api.findKnownCategories()) {
            if (slot >= 45) break;
            String permission = "headdb.category." + normalize(category);
            if (!player.hasPermission("headdb.category.*") && !player.hasPermission(permission)) continue;
            inventory.setItem(slot, item(material("CHEST", "CHEST"), ChatColor.GOLD + category));
            holder.actions.put(slot++, "category:" + category);
        }
        inventory.setItem(45, item(material("NETHER_STAR", "GOLD_INGOT"), ChatColor.YELLOW + "Favorites"));
        holder.actions.put(45, "favorites");
        inventory.setItem(46, named(LegacyItemFactory.newPlayerHead(), ChatColor.AQUA + "Local Heads"));
        holder.actions.put(46, "local");
        inventory.setItem(47, item(material("BOOKSHELF", "BOOKSHELF"), ChatColor.LIGHT_PURPLE + "More Categories"));
        holder.actions.put(47, "custom");
        player.openInventory(inventory);
        sounds.play(player, "menu.open");
    }

    void openCategory(Player player, String category, int page) {
        if (!allowed(player, category)) { deny(player); return; }
        openHeads(player, "category", category, database.getByCategory(category), page,
                ChatColor.RED + "HeadDB " + ChatColor.GRAY + "» " + ChatColor.GOLD + category);
    }

    void openSearch(Player player, String query, List<Head> heads) {
        openHeads(player, "search", query, heads, 0,
                ChatColor.RED + "HeadDB " + ChatColor.GRAY + "» " + ChatColor.GOLD + query);
    }

    void openFavorites(Player player, int page) {
        if (!allowed(player, "favorites")) { deny(player); return; }
        List<Head> heads = new ArrayList<Head>();
        LegacyPlayerData data = storage.get(player.getUniqueId());
        for (Integer id : data.getFavorites()) {
            Head head = database.getById(id);
            if (head != null) heads.add(head);
        }
        List<UUID> locals = data.getLocalFavorites();
        int total = heads.size() + locals.size();
        int maxPage = maxPage(total);
        page = Math.max(0, Math.min(page, maxPage));
        MenuHolder holder = new MenuHolder("favorites", "", page, heads);
        Inventory inventory = create(holder, 54, messages.get("menu.favorites.name", "HeadDB » Favorites"));
        int start = page * PAGE_SIZE;
        for (int index = start; index < Math.min(start + PAGE_SIZE, total); index++) {
            int slot = index - start;
            if (index < heads.size()) {
                Head head = heads.get(index);
                inventory.setItem(slot, head.getItem());
                holder.actions.put(slot, "head:" + head.getId());
            } else {
                UUID id = locals.get(index - heads.size());
                OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
                if (offline.getName() != null) {
                    inventory.setItem(slot, localHead(offline));
                    holder.actions.put(slot, "local-head:" + id);
                }
            }
        }
        addControls(inventory, holder, page, maxPage);
        player.openInventory(inventory);
    }

    void openCustomCategories(Player player) {
        if (!allowed(player, "custom")) { deny(player); return; }
        MenuHolder holder = new MenuHolder("custom", "", 0, Collections.<Head>emptyList());
        Inventory inventory = create(holder, 54, messages.get("menu.customCategories.name", "HeadDB » More Categories"));
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "categories.yml"));
        int slot = 0;
        for (String key : config.getKeys(false)) {
            if (slot >= 45 || !config.getBoolean(key + ".enabled", true)) continue;
            String name = LegacyMessages.color(config.getString(key + ".icon.name", key));
            String texture = config.getString(key + ".icon.head");
            ItemStack icon = texture == null ? item(material("BOOKSHELF", "BOOKSHELF"), name)
                    : customTexture(texture, name);
            inventory.setItem(slot, icon);
            holder.actions.put(slot++, "custom-category:" + key);
        }
        addBack(inventory, holder);
        player.openInventory(inventory);
    }

    private void openCustomCategory(Player player, String key, int page) {
        if (!allowed(player, key)) { deny(player); return; }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "categories.yml"));
        List<Head> heads = new ArrayList<Head>();
        for (String texture : config.getStringList(key + ".heads")) {
            Head head = database.getByTexture(texture);
            if (head != null) heads.add(head);
        }
        String name = LegacyMessages.color(config.getString(key + ".icon.name", key));
        openHeads(player, "custom-heads", key, heads, page, ChatColor.RED + "HeadDB » " + name);
    }

    private void openLocal(Player player, int page) {
        if (!allowed(player, "local")) { deny(player); return; }
        OfflinePlayer[] players = Bukkit.getOfflinePlayers();
        int maxPage = maxPage(players.length);
        page = Math.max(0, Math.min(page, maxPage));
        MenuHolder holder = new MenuHolder("local", "", page, Collections.<Head>emptyList());
        Inventory inventory = create(holder, 54, messages.get("menu.local.name", "HeadDB » Local Heads"));
        int start = page * PAGE_SIZE;
        for (int i = start; i < Math.min(start + PAGE_SIZE, players.length); i++) {
            OfflinePlayer offline = players[i];
            if (offline.getName() == null) continue;
            ItemStack icon = localHead(offline);
            inventory.setItem(i - start, icon);
            holder.actions.put(i - start, "local-head:" + offline.getUniqueId());
        }
        addControls(inventory, holder, page, maxPage);
        player.openInventory(inventory);
    }

    private void openHeads(Player player, String type, String key, List<Head> heads, int page, String title) {
        int maxPage = maxPage(heads.size());
        page = Math.max(0, Math.min(page, maxPage));
        MenuHolder holder = new MenuHolder(type, key, page, heads);
        Inventory inventory = create(holder, 54, title);
        int start = page * PAGE_SIZE;
        LegacyPlayerData data = storage.get(player.getUniqueId());
        for (int i = start; i < Math.min(start + PAGE_SIZE, heads.size()); i++) {
            Head head = heads.get(i);
            ItemStack icon = head.getItem();
            ItemMeta meta = icon.getItemMeta();
            List<String> lore = meta.hasLore() ? new ArrayList<String>(meta.getLore()) : new ArrayList<String>();
            lore.add("");
            lore.add(ChatColor.YELLOW + "Left click: Take");
            lore.add((data.isFavorite(head.getId()) ? ChatColor.RED + "Right click: Unfavorite"
                    : ChatColor.GREEN + "Right click: Favorite"));
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(i - start, icon);
            holder.actions.put(i - start, "head:" + head.getId());
        }
        addControls(inventory, holder, page, maxPage);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder) || event.getRawSlot() < 0
                || event.getRawSlot() >= top.getSize()) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        MenuHolder holder = (MenuHolder) top.getHolder();
        String action = holder.actions.get(event.getRawSlot());
        if (action == null) return;

        if (action.equals("main")) openMain(player);
        else if (action.equals("back")) reopen(holder, player, holder.page - 1);
        else if (action.equals("next")) reopen(holder, player, holder.page + 1);
        else if (action.equals("favorites")) openFavorites(player, 0);
        else if (action.equals("local")) openLocal(player, 0);
        else if (action.equals("custom")) openCustomCategories(player);
        else if (action.startsWith("category:")) openCategory(player, action.substring(9), 0);
        else if (action.startsWith("custom-category:")) openCustomCategory(player, action.substring(16), 0);
        else if (action.startsWith("head:")) clickHead(player, Integer.parseInt(action.substring(5)), event.isRightClick(), holder);
        else if (action.startsWith("local-head:")) clickLocalHead(player, UUID.fromString(action.substring(11)), event.isRightClick(), holder);
        else if (action.startsWith("purchase:")) purchase(player, action);
    }

    private void clickHead(Player player, int id, boolean favoriteClick, MenuHolder holder) {
        Head head = database.getById(id);
        if (head == null) return;
        if (!allowed(player, head.getCategory())) { deny(player); return; }
        if (favoriteClick) {
            boolean added = storage.get(player.getUniqueId()).toggleFavorite(id);
            player.sendMessage(ChatColor.GOLD + head.getName() + (added ? ChatColor.GREEN + " added to favorites."
                    : ChatColor.RED + " removed from favorites."));
            sounds.play(player, added ? "favorite.add" : "favorite.remove");
            reopen(holder, player, holder.page);
            return;
        }
        if (economy.isEnabled()) openPurchase(player, head);
        else {
            give(player, head.getItem());
            sounds.play(player, "head.take");
        }
    }

    private void openPurchase(Player player, Head head) {
        MenuHolder holder = new MenuHolder("purchase", String.valueOf(head.getId()), 0, Collections.<Head>emptyList());
        Inventory inventory = create(holder, 27, messages.get("menu.purchase.name", "HeadDB » Purchase",
                "name", head.getName()));
        int[] amounts = {1, 8, 16, 32, 64};
        int[] slots = {10, 11, 13, 15, 16};
        double unit = economy.price(head);
        for (int i = 0; i < amounts.length; i++) {
            ItemStack icon = head.getItem();
            icon.setAmount(Math.min(amounts[i], icon.getMaxStackSize()));
            ItemMeta meta = icon.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + String.valueOf(amounts[i]) + "x " + head.getName());
            List<String> lore = new ArrayList<String>();
            lore.add(ChatColor.GRAY + "Cost: " + ChatColor.GOLD + (unit * amounts[i]));
            meta.setLore(lore);
            icon.setItemMeta(meta);
            inventory.setItem(slots[i], icon);
            holder.actions.put(slots[i], "purchase:" + head.getId() + ":" + amounts[i]);
        }
        addBack(inventory, holder);
        player.openInventory(inventory);
    }

    private void purchase(Player player, String action) {
        String[] parts = action.split(":");
        Head head = database.getById(Integer.parseInt(parts[1]));
        int amount = Integer.parseInt(parts[2]);
        if (head == null) return;
        double total = economy.price(head) * amount;
        if (!economy.purchase(player, total)) {
            player.sendMessage(messages.get("purchase.invalidFunds", "You do not have enough money."));
            sounds.play(player, "purchase.failed");
            return;
        }
        int remaining = amount;
        while (remaining > 0) {
            ItemStack item = head.getItem();
            int stack = Math.min(remaining, item.getMaxStackSize());
            item.setAmount(stack);
            give(player, item);
            remaining -= stack;
        }
        player.closeInventory();
        player.sendMessage(messages.get("purchase.success", "Bought {amount}x {name} for {cost}",
                "amount", String.valueOf(amount), "name", head.getName(), "cost", String.valueOf(total)));
        sounds.play(player, "purchase.completed");
    }

    private void clickLocalHead(Player player, UUID id, boolean favoriteClick, MenuHolder holder) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
        if (favoriteClick) {
            storage.get(player.getUniqueId()).toggleLocalFavorite(id);
            reopen(holder, player, holder.page);
            return;
        }
        give(player, localHead(offline));
        sounds.play(player, "head.take");
    }

    private void reopen(MenuHolder holder, Player player, int page) {
        if (holder.type.equals("category")) openCategory(player, holder.key, page);
        else if (holder.type.equals("favorites")) openFavorites(player, page);
        else if (holder.type.equals("local")) openLocal(player, page);
        else if (holder.type.equals("custom-heads")) openCustomCategory(player, holder.key, page);
        else if (holder.type.equals("search")) openHeads(player, "search", holder.key, holder.heads, page,
                ChatColor.RED + "HeadDB » " + ChatColor.GOLD + holder.key);
        else if (holder.type.equals("purchase")) openMain(player);
        else openMain(player);
    }

    private void give(Player player, ItemStack item) {
        item = LegacyItemFactory.prepareForGive(item);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) player.getWorld().dropItemNaturally(player.getLocation(), leftover);
    }

    private void addControls(Inventory inventory, MenuHolder holder, int page, int maxPage) {
        if (page > 0) {
            inventory.setItem(45, item(material("ARROW", "ARROW"), ChatColor.GOLD + "Previous"));
            holder.actions.put(45, "back");
        }
        inventory.setItem(49, item(material("PAPER", "PAPER"), ChatColor.GOLD + "Page " + (page + 1) + "/" + (maxPage + 1)));
        holder.actions.put(49, "main");
        if (page < maxPage) {
            inventory.setItem(53, item(material("ARROW", "ARROW"), ChatColor.GOLD + "Next"));
            holder.actions.put(53, "next");
        }
    }

    private void addBack(Inventory inventory, MenuHolder holder) {
        int slot = inventory.getSize() - 5;
        inventory.setItem(slot, item(material("ARROW", "ARROW"), ChatColor.GOLD + "Back"));
        holder.actions.put(slot, "main");
    }

    private Inventory create(MenuHolder holder, int size, String title) {
        String safe = title == null ? "HeadDB" : title;
        if (safe.length() > 32) safe = safe.substring(0, 32);
        Inventory inventory = Bukkit.createInventory(holder, size, safe);
        holder.inventory = inventory;
        return inventory;
    }

    private ItemStack localHead(OfflinePlayer player) {
        ItemStack item = LegacyItemFactory.newPlayerHead();
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) item.getItemMeta();
        meta.setOwner(player.getName());
        meta.setDisplayName(ChatColor.RESET + player.getName());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack customTexture(String texture, String name) {
        // A transient model keeps texture creation in the cross-version factory.
        return named(LegacyItemFactory.createTextureHead(texture), name);
    }

    private static ItemStack named(ItemStack item, String name) {
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack item(Material material, String name) { return named(new ItemStack(material), name); }
    private static Material material(String preferred, String fallback) {
        Material value = Material.matchMaterial(preferred);
        if (value == null) value = Material.matchMaterial(fallback);
        return value == null ? Material.STONE : value;
    }
    private static int maxPage(int size) { return Math.max(0, (size - 1) / PAGE_SIZE); }
    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_").replaceAll("^_+|_+$", "");
    }
    private boolean allowed(Player player, String category) {
        return player.hasPermission("headdb.category.*")
                || player.hasPermission("headdb.category." + normalize(category));
    }
    private void deny(Player player) {
        player.sendMessage(messages.get("noPermission", "No permission!"));
        sounds.play(player, "noPermission");
    }

    private static final class MenuHolder implements InventoryHolder {
        private final String type;
        private final String key;
        private final int page;
        private final List<Head> heads;
        private final Map<Integer, String> actions = new HashMap<Integer, String>();
        private Inventory inventory;
        private MenuHolder(String type, String key, int page, List<Head> heads) {
            this.type = type; this.key = key; this.page = page; this.heads = heads;
        }
        @Override public Inventory getInventory() { return inventory; }
    }
}
