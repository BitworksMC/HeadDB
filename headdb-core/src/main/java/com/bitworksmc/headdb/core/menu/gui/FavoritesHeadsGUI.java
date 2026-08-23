package com.bitworksmc.headdb.core.menu.gui;

import com.github.thesilentpro.grim.button.SimpleButton;
import com.github.thesilentpro.grim.page.Page;
import com.bitworksmc.headdb.api.model.Head;
import com.bitworksmc.headdb.core.HeadDB;
import com.bitworksmc.headdb.core.menu.FavoritesHeadsMenu;
import com.bitworksmc.headdb.core.util.Compatibility;
import com.bitworksmc.headdb.core.util.Utils;
import com.bitworksmc.headdb.core.util.WebsiteLinks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class FavoritesHeadsGUI extends SafePaginatedGUI {
    public FavoritesHeadsGUI(HeadDB plugin, String key, Component title, List<Head> heads, List<ItemStack> items) {
        super(new NamespacedKey(plugin, Utils.normalizeNamespacedKey("gui_" + key)));

        String submissionUrl = WebsiteLinks.submissionUrl(plugin.getCfg().getWebsiteUrl());
        int pageSize = plugin.getCfg().getHeadsMenuRows() * 9;
        for (PageRange range : calculatePageRanges(heads.size(), items.size(), pageSize)) {
            List<Head> headsChunk = heads.subList(range.headFrom(), range.headTo());
            List<ItemStack> itemsChunk = items.subList(range.itemFrom(), range.itemTo());

            FavoritesHeadsMenu page = new FavoritesHeadsMenu(plugin, this, title, headsChunk, itemsChunk);

            // Divider
            if (plugin.getCfg().isHeadsMenuDividerEnabled()) {
                ItemStack dividerItem = Compatibility.newItem(
                        plugin.getCfg().getHeadsMenuDividerMaterial(),
                        MiniMessage.miniMessage().deserialize(plugin.getCfg().getHeadsMenuDividerName())
                );
                int startSlot = (plugin.getCfg().getDividerRow() - 1) * 9;
                for (int i = startSlot; i < startSlot + 9; i++) {
                    page.setButton(i, new SimpleButton(dividerItem));
                }
            }

            // Info item
            if (plugin.getCfg().isShowInfoItem()) {
                Component[] infoLore = new Component[]{
                        Component.text("❓ Didn't spot the perfect head in our collection?")
                                .decoration(TextDecoration.ITALIC, false)
                                .color(NamedTextColor.YELLOW),
                        Component.text("🎯 We're always adding more — and you can help!")
                                .decoration(TextDecoration.ITALIC, false)
                                .color(NamedTextColor.YELLOW),
                        Component.text(""),
                        Component.text("📥 Submit your favorite or original heads")
                                .decoration(TextDecoration.ITALIC, false)
                                .color(NamedTextColor.YELLOW),
                        Component.text("✨ Send it through headdb.net for review!")
                                .decoration(TextDecoration.ITALIC, false)
                                .color(NamedTextColor.YELLOW),
                        Component.text(""),
                        Component.text("🔗 Submit > " + submissionUrl)
                                .decoration(TextDecoration.ITALIC, false)
                                .color(NamedTextColor.YELLOW)
                };

                ItemStack infoItem = plugin.getHeadApi()
                        .findByTexture("16439d2e306b225516aa9a6d007a7e75edd2d5015d113b42f44be62a517e574f")
                        .join()
                        .map(head -> Compatibility.setItemDetails(
                                head.getItem(),
                                Component.text("Can't find the head you're looking for?").color(NamedTextColor.RED),
                                infoLore
                        ))
                        .orElseGet(() -> Compatibility.newItem(
                                Material.WRITABLE_BOOK,
                                Component.text("Can't find the head you're looking for?").color(NamedTextColor.RED),
                                infoLore
                        ));

                page.setButton(53, new SimpleButton(infoItem, ctx -> {
                    Compatibility.sendMessage(
                            ctx.event().getWhoClicked(),
                            WebsiteLinks.makeClickable(
                                    Component.text("Click to submit a head: " + submissionUrl)
                                            .color(NamedTextColor.AQUA)
                                            .decoration(TextDecoration.UNDERLINED, true),
                                    submissionUrl,
                                    Component.text("Open " + submissionUrl, NamedTextColor.AQUA)
                            )
                    );
                }));
            }

            addPage(page);
        }

        // ── CONTROLS ────────────────────────────────────────────────────────────────

        // Back button
        ItemStack backItem = plugin.getHeadApi()
                .findByTexture(plugin.getCfg().getBackTexture())
                .join()
                .map(head -> Compatibility.setItemDetails(
                        head.getItem(),
                        plugin.getLocalization().getConsoleMessage("menu.controls.back.name").orElseGet(() -> Component.text("◀ Back")),
                        plugin.getLocalization().getConsoleMessage("menu.controls.back.lore").orElseGet(() -> Component.text("Takes you to the previous page: ${{BACK}}"))
                ))
                .orElseGet(() -> Compatibility.newItem(
                        plugin.getCfg().getBackItem(),
                        plugin.getLocalization().getConsoleMessage("menu.controls.back.name").orElseGet(() -> Component.text("◀ Back")),
                        plugin.getLocalization().getConsoleMessage("menu.controls.back.lore").orElseGet(() -> Component.text("Takes you to the previous page: ${{BACK}}"))
                ));

        // Page‐info button
        ItemStack pageInfoItem = plugin.getHeadApi()
                .findByTexture(plugin.getCfg().getInfoTexture())
                .join()
                .map(head -> Compatibility.setItemDetails(
                        head.getItem(),
                        plugin.getLocalization().getConsoleMessage("menu.controls.info.name").orElseGet(() ->
                                Component.text("ℹ Page ")
                                        .append(Component.text("${{CURRENT}}").color(NamedTextColor.GREEN))
                                        .append(Component.text("/").color(NamedTextColor.GRAY))
                                        .append(Component.text("${{MAX}}").color(NamedTextColor.RED))
                        ),
                        plugin.getLocalization().getConsoleMessage("menu.controls.info.lore").orElseGet(() ->
                                Component.text("Click here to go to the main menu.")
                        )
                ))
                .orElseGet(() -> Compatibility.newItem(
                        plugin.getCfg().getInfoItem(),
                        plugin.getLocalization().getConsoleMessage("menu.controls.info.name").orElseGet(() ->
                                Component.text("ℹ Page ")
                                        .append(Component.text("${{CURRENT}}").color(NamedTextColor.GREEN))
                                        .append(Component.text("/").color(NamedTextColor.GRAY))
                                        .append(Component.text("${{MAX}}").color(NamedTextColor.RED))
                        ),
                        plugin.getLocalization().getConsoleMessage("menu.controls.info.lore").orElseGet(() ->
                                Component.text("Click here to go to the main menu.")
                        )
                ));
        // Next button
        ItemStack nextItem = plugin.getHeadApi()
                .findByTexture(plugin.getCfg().getNextTexture())
                .join()
                .map(head -> Compatibility.setItemDetails(
                        head.getItem(),
                        plugin.getLocalization().getConsoleMessage("menu.controls.next.name").orElseGet(() -> Component.text("Next ▶")),
                        plugin.getLocalization().getConsoleMessage("menu.controls.next.lore").orElseGet(() -> Component.text("Takes you to the next page: ${{NEXT}}"))
                ))
                .orElseGet(() -> Compatibility.newItem(
                        plugin.getCfg().getNextItem(),
                        plugin.getLocalization().getConsoleMessage("menu.controls.next.name").orElseGet(() -> Component.text("Next ▶")),
                        plugin.getLocalization().getConsoleMessage("menu.controls.next.lore").orElseGet(() -> Component.text("Takes you to the next page: ${{NEXT}}"))
                ));

        setControls(
                new SimpleButton(backItem, ctx -> {
                    Compatibility.playSound((Player) ctx.event().getWhoClicked(), plugin.getSoundConfig().get("control.back"));
                }),
                new SimpleButton(pageInfoItem, ctx -> {
                    plugin.getMenuManager().getMainMenu().open((Player) ctx.event().getWhoClicked());
                    Compatibility.playSound((Player) ctx.event().getWhoClicked(), plugin.getSoundConfig().get("control.info"));
                }),
                new SimpleButton(nextItem, ctx -> {
                    Compatibility.playSound((Player) ctx.event().getWhoClicked(), plugin.getSoundConfig().get("control.next"));
                }),
                true
        );

        // Ensure page info name is visible on Paper while preserving translated values.
        if (Compatibility.IS_PAPER) {
            getPages().values().forEach(page -> {
                if (page instanceof com.github.thesilentpro.grim.page.controllable.PaginatedControllable controllable) {
                    page.updateButton(controllable.getCurrentSlot(), button ->
                            button.getItem().ifPresent(item -> {
                                var meta = item.getItemMeta();
                                Component name = meta.itemName();
                                if (name != null) {
                                    meta.displayName(name.decoration(TextDecoration.ITALIC, false));
                                    item.setItemMeta(meta);
                                    button.setItem(item);
                                }
                            })
                    );
                }
            });
        }

        getPages().values().forEach(Page::reRender);
    }

    /**
     * Packs database and local favorites into one shared page capacity. Chunking
     * each list independently can put twice the configured number of buttons on
     * a page when a player has favorites of both kinds.
     */
    static List<PageRange> calculatePageRanges(int headCount, int itemCount, int pageSize) {
        if (headCount < 0 || itemCount < 0) {
            throw new IllegalArgumentException("Favorite counts cannot be negative");
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("Page size must be positive");
        }

        int total = Math.addExact(headCount, itemCount);
        List<PageRange> pages = new ArrayList<>((total + pageSize - 1) / pageSize);
        for (int offset = 0; offset < total; offset += pageSize) {
            int pageEnd = Math.min(total, offset + pageSize);
            int headFrom = Math.min(offset, headCount);
            int headTo = Math.min(pageEnd, headCount);
            int itemFrom = Math.max(0, offset - headCount);
            int itemTo = Math.max(0, pageEnd - headCount);
            pages.add(new PageRange(headFrom, headTo, itemFrom, itemTo));
        }
        return pages;
    }

    record PageRange(int headFrom, int headTo, int itemFrom, int itemTo) {
    }
}
