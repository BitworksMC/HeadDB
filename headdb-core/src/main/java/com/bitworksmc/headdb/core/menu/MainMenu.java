package com.bitworksmc.headdb.core.menu;

import com.github.thesilentpro.grim.button.Button;
import com.github.thesilentpro.grim.button.SimpleButton;
import com.github.thesilentpro.grim.page.Page;
import com.github.thesilentpro.grim.page.SimplePage;
import com.bitworksmc.headdb.api.model.Head;
import com.bitworksmc.headdb.core.HeadDB;
import com.bitworksmc.headdb.core.menu.gui.CustomCategoriesGUI;
import com.bitworksmc.headdb.core.menu.gui.FavoritesHeadsGUI;
import com.bitworksmc.headdb.core.menu.gui.HeadsGUI;
import com.bitworksmc.headdb.core.menu.gui.LocalHeadsGUI;
import com.bitworksmc.headdb.core.storage.PlayerData;
import com.bitworksmc.headdb.core.util.Compatibility;
import com.bitworksmc.headdb.core.util.PermissionUtil;
import com.github.thesilentpro.inputs.paper.PaperInput;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MainMenu extends SimplePage {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainMenu.class);
    private static final String DISCORD_URL = "https://discord.gg/j8BAsz8Ac7";
    private static final int[] CATEGORY_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33};

    public MainMenu(HeadDB plugin) {
        super(plugin.getLocalization().getConsoleMessage("menu.main.name").orElse(Component.text("HeadDB").color(NamedTextColor.RED)), 6);
        preventInteraction();

        plugin.getHeadApi().onReady().thenAcceptAsync(heads -> {
            LOGGER.debug("RENDER THREAD = {}", Thread.currentThread().getName());
            renderCategoryButtons(plugin, heads);
            renderLocalButton(plugin);
            renderFavoritesButton(plugin);
            renderCustomCategoriesButton(plugin);
            renderSearchButton(plugin);
            renderInfoButton(plugin);
            fillBorder(this);
            reRender();
        }, Compatibility.getMainThreadExecutor(plugin));
    }

    private void renderCategoryButtons(HeadDB plugin, List<Head> heads) {
        List<String> preferredOrder = List.of("Alphabet", "Animals", "Blocks", "Decoration", "Food & Drinks", "Humanoid", "Humans", "Miscellaneous", "Monsters", "Plants");

        Map<String, Head> headByCategory = new HashMap<>();
        for (Head head : heads) {
            headByCategory.putIfAbsent(head.getCategory(), head);
        }

        List<String> orderedCategories = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        preferredOrder.stream().filter(headByCategory::containsKey).forEach(cat -> {
            orderedCategories.add(cat);
            seen.add(cat);
        });
        headByCategory.keySet().stream().filter(seen::add).forEach(orderedCategories::add);

        for (int i = 0; i < Math.min(CATEGORY_SLOTS.length, orderedCategories.size()); i++) {
            String category = orderedCategories.get(i);
            Head head = headByCategory.get(category);
            if (head == null) {
                continue;
            }

            Component name = plugin.getLocalization().getConsoleMessage("menu.main.category." + category.toLowerCase(Locale.ROOT) + ".name")
                    .orElse(Component.text(category).color(NamedTextColor.GOLD));
            ItemStack item = Compatibility.setItemDetails(head.getItem(), name, Component.text(""));

            setButton(CATEGORY_SLOTS[i], new SimpleButton(item, ctx -> {
                Player player = (Player) ctx.event().getWhoClicked();
                if (!PermissionUtil.hasCategoryPermission(player, category)) {
                    plugin.getLocalization().sendMessage(player, "noPermission");
                    Compatibility.playSound(player, plugin.getSoundConfig().get("noPermission"));
                    return;
                }

                HeadsGUI gui = plugin.getMenuManager().get(category.replace(" ", "_").replace("&", "_"));
                int page = plugin.getCfg().isTrackPage() ? gui.getGuiRegistry().getCurrentPage(player.getUniqueId(), gui.getKey()).orElse(0) : 0;
                gui.open(player, page);
                Compatibility.playSound(player, plugin.getSoundConfig().get("menu.open"));
            }));
        }
    }

    private void renderLocalButton(HeadDB plugin) {
        ItemStack item = plugin.getHeadApi()
                .findByTexture("7f6bf958abd78295eed6ffc293b1aa59526e80f54976829ea068337c2f5e8")
                .join()
                .map(head -> Compatibility.setItemDetails(head.getItem(), getMsg(plugin, "menu.main.local.name", "Local Heads", NamedTextColor.AQUA), Component.text("")))
                .orElse(Compatibility.newItem(Material.COMPASS, getMsg(plugin, "menu.main.local.name", "Local Heads", NamedTextColor.AQUA), Component.text("")));

        setButton(50, new SimpleButton(item, ctx -> {
            Player player = (Player) ctx.event().getWhoClicked();
            if (!PermissionUtil.hasCategoryPermission(player, "local")) {
                plugin.getLocalization().sendMessage(player, "noPermission");
                Compatibility.playSound(player, plugin.getSoundConfig().get("noPermission"));
                return;
            }

            List<ItemStack> localHeads = plugin.getHeadApi().computeLocalHeads();
            if (localHeads.isEmpty()) {
                plugin.getLocalization().sendMessage(player, "localNone");
                Compatibility.playSound(player, plugin.getSoundConfig().get("menu.none"));
                return;
            }

            LocalHeadsGUI gui = new LocalHeadsGUI(plugin, "local_" + player.getUniqueId(), getMsg(plugin, "menu.local.name", "HeadDB » Local", NamedTextColor.GOLD), localHeads);
            int page = plugin.getCfg().isTrackPage() ? gui.getGuiRegistry().getCurrentPage(player.getUniqueId(), gui.getKey()).orElse(0) : 0;
            gui.open(player, page);
            Compatibility.playSound(player, plugin.getSoundConfig().get("menu.open"));
        }));
    }

    private void renderFavoritesButton(HeadDB plugin) {
        ItemStack item = plugin.getHeadApi()
                .findByTexture("76fdd4b13d54f6c91dd5fa765ec93dd9458b19f8aa34eeb5c80f455b119f278")
                .join()
                .map(head -> Compatibility.setItemDetails(head.getItem(), getMsg(plugin, "menu.main.favorites.name", "Favorites", NamedTextColor.YELLOW), Component.text("")))
                .orElse(Compatibility.newItem(Material.BOOK, getMsg(plugin, "menu.main.favorites.name", "Favorites", NamedTextColor.YELLOW), Component.text("")));

        setButton(51, new SimpleButton(item, ctx -> {
            Player player = (Player) ctx.event().getWhoClicked();
            if (!PermissionUtil.hasCategoryPermission(player, "favorites")) {
                plugin.getLocalization().sendMessage(player, "noPermission");
                Compatibility.playSound(player, plugin.getSoundConfig().get("noPermission"));
                return;
            }

            UUID playerId = player.getUniqueId();
            PlayerData data = plugin.getPlayerStorage().getPlayer(playerId);
            List<CompletableFuture<Optional<Head>>> futures = data.getFavorites().stream()
                    .map(plugin.getHeadApi()::findById)
                    .toList();

            List<ItemStack> localItems = data.getLocalFavorites().stream()
                    .map(plugin.getHeadApi()::computeLocalHead)
                    .filter(Optional::isPresent).map(Optional::get).toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> futures.stream().map(CompletableFuture::join)
                            .filter(Optional::isPresent).map(Optional::get).toList())
                    .thenAcceptAsync(favoriteHeads -> {
                        if (favoriteHeads.isEmpty() && localItems.isEmpty()) {
                            plugin.getLocalization().sendMessage(player, "favoritesNone");
                            Compatibility.playSound(player, plugin.getSoundConfig().get("menu.none"));
                            return;
                        }
                        FavoritesHeadsGUI gui = new FavoritesHeadsGUI(plugin, "favorites_" + playerId, getMsg(plugin, "menu.favorites.name", "HeadDB » Favorites", NamedTextColor.GOLD), favoriteHeads, localItems);
                        int page = plugin.getCfg().isTrackPage() ? gui.getGuiRegistry().getCurrentPage(playerId, gui.getKey()).orElse(0) : 0;
                        gui.open(player, page);
                        Compatibility.playSound(player, plugin.getSoundConfig().get("menu.open"));
                    }, Compatibility.getEntityExecutor(plugin, player))
                    .exceptionally(ex -> {
                        LOGGER.error("Failed to compute favorite heads for: {}", playerId, ex);
                        return null;
                    });
        }));
    }

    private void renderCustomCategoriesButton(HeadDB plugin) {
        ItemStack item = plugin.getHeadApi().findByTexture(plugin.getCfg().getCustomCategoryTexture()).join()
                .map(head -> Compatibility.setItemDetails(head.getItem(), getMsg(plugin, "menu.main.customCategories.name", "More Categories", NamedTextColor.DARK_PURPLE), Component.text("")))
                .orElse(Compatibility.newItem(plugin.getCfg().getCustomCategoryItem(), getMsg(plugin, "menu.main.customCategories.name", "More Categories", NamedTextColor.DARK_PURPLE), Component.text("")));

        setButton(47, new SimpleButton(item, ctx -> {
            Player player = (Player) ctx.event().getWhoClicked();
            if (!PermissionUtil.hasCategoryPermission(player, "custom")) {
                plugin.getLocalization().sendMessage(player, "noPermission");
                Compatibility.playSound(player, plugin.getSoundConfig().get("noPermission"));
                return;
            }

            CustomCategoriesGUI gui = plugin.getMenuManager().getCustomCategoriesGui();
            if (gui.getPages().isEmpty()) {
                plugin.getLocalization().sendMessage(player, "customCategoriesNone");
                Compatibility.playSound(player, plugin.getSoundConfig().get("menu.none"));
                return;
            }

            int page = plugin.getCfg().isTrackPage() ?
                    gui.getGuiRegistry().getCurrentPage(player.getUniqueId(), gui.getKey()).orElse(0) : 0;
            gui.open(player, page);
            Compatibility.playSound(player, plugin.getSoundConfig().get("menu.open"));
        }));
    }

    private void renderSearchButton(HeadDB plugin) {
        ItemStack item = plugin.getHeadApi().findByTexture(plugin.getCfg().getSearchTexture()).join()
                .map(head -> Compatibility.setItemDetails(head.getItem(), getMsg(plugin, "menu.main.search.name", "Search", NamedTextColor.GREEN), Component.text("")))
                .orElse(Compatibility.newItem(plugin.getCfg().getSearchItem(), getMsg(plugin, "menu.main.search.name", "Search", NamedTextColor.GREEN), Component.text("")));

        setButton(48, new SimpleButton(item, ctx -> {
            if (!ctx.event().getWhoClicked().hasPermission("headdb.command.search")) {
                plugin.getLocalization().sendMessage(ctx.event().getWhoClicked(), "noPermission");
                Compatibility.playSound(ctx.event().getWhoClicked(), plugin.getSoundConfig().get("noPermission"));
                return;
            }
            if (!Compatibility.IS_PAPER) {
                return;
            }

            Player player = (Player) ctx.event().getWhoClicked();
            player.closeInventory();
            plugin.getLocalization().sendMessage(player, "command.search.input");
            Compatibility.playSound(player, plugin.getSoundConfig().get("input.wait"));
            PaperInput.awaitString().then((input, event) -> {
                event.setCancelled(true);
                Compatibility.getEntityExecutor(plugin, player).execute(() -> player.performCommand("hdb search " + input));
            }).register(player.getUniqueId());
        }));
    }

    private void renderInfoButton(HeadDB plugin) {
        if (!plugin.getCfg().isShowInfoItem()) {
            return;
        }

        Component[] lore = new Component[]{
                Component.text("❓ Didn't spot the perfect head in our collection?").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text("🎯 We're always adding more — and you can help!").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text(""),
                Component.text("📥 Submit your favorite or original heads").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text("✨ Directly through our community Discord!").color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text(""),
                Component.text("🔗 Discord > " + DISCORD_URL).color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)
        };

        ItemStack item = plugin.getHeadApi()
                .findByTexture("16439d2e306b225516aa9a6d007a7e75edd2d5015d113b42f44be62a517e574f")
                .join()
                .map(head -> Compatibility.setItemDetails(head.getItem(), Component.text("Can't find the head you're looking for?").color(NamedTextColor.RED), lore))
                .orElse(Compatibility.newItem(Material.BOOK, Component.text("Can't find the head you're looking for?").color(NamedTextColor.RED), lore));

        setButton(53, new SimpleButton(item, ctx -> Compatibility.sendMessage(
                ctx.event().getWhoClicked(),
                Component.text("Click to join: " + DISCORD_URL)
                        .color(NamedTextColor.AQUA)
                        .clickEvent(ClickEvent.openUrl(DISCORD_URL))
                        .decoration(TextDecoration.UNDERLINED, true)
        )));
    }

    private void fillBorder(Page page) {
        Button filler = new SimpleButton(Compatibility.newItem(Material.BLACK_STAINED_GLASS_PANE, Component.text("")));
        for (int i = 0; i < page.getSize(); i++) {
            if (page.getButton(i).isEmpty()) {
                page.setButton(i, filler);
            }
        }
    }

    private Component getMsg(HeadDB plugin, String key, String fallback, NamedTextColor color) {
        return plugin.getLocalization().getConsoleMessage(key).orElse(Component.text(fallback).color(color));
    }
}
