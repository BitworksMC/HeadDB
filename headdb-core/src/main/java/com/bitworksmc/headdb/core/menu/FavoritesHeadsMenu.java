package com.bitworksmc.headdb.core.menu;

import com.github.thesilentpro.grim.button.SimpleButton;
import com.github.thesilentpro.grim.gui.GUI;
import com.github.thesilentpro.grim.page.PaginatedSimplePage;
import com.bitworksmc.headdb.api.model.Head;
import com.bitworksmc.headdb.core.HeadDB;
import com.bitworksmc.headdb.core.factory.ItemFactoryRegistry;
import com.bitworksmc.headdb.core.storage.PlayerData;
import com.bitworksmc.headdb.core.util.Compatibility;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public class FavoritesHeadsMenu extends PaginatedSimplePage {

    public FavoritesHeadsMenu(HeadDB plugin, GUI<Integer> gui, Component title, List<Head> heads, List<ItemStack> items) {
        super(gui, title, 6, 48, 49, 50);
        preventInteraction();

        for (Head head : heads) {
            addButton(new SimpleButton(head.getItem(), ctx -> {
                Player player = (Player) ctx.event().getWhoClicked();
                ClickType click = ctx.event().getClick();

                if (!player.hasPermission("headdb.category." + head.getCategory())) {
                    plugin.getLocalization().sendMessage(player, "noPermission");
                    Compatibility.playSound(player, plugin.getSoundConfig().get("noPermission"));
                    return;
                }

                PlayerData playerData = plugin.getPlayerStorage().getPlayer(player.getUniqueId());

                if (click == ClickType.RIGHT) {
                    if (playerData.getFavorites().contains(head.getId())) {
                        playerData.removeFavorite(head.getId());
                        plugin.getLocalization().sendMessage(player, "menu.favorites.remove", msg -> msg.replaceText(builder -> builder.matchLiteral("{name}").replacement(head.getName())));
                        Compatibility.playSound(player, plugin.getSoundConfig().get("favorite.remove"));
                    } else {
                        playerData.addFavorite(head.getId());
                        plugin.getLocalization().sendMessage(player, "menu.favorites.add", msg -> msg.replaceText(builder -> builder.matchLiteral("{name}").replacement(head.getName())));
                        Compatibility.playSound(player, plugin.getSoundConfig().get("favorite.add"));
                    }
                    return;
                }

                if (plugin.getEconomyProvider() != null) {
                    new PurchaseHeadMenu(plugin, player, head, this).open(player);
                } else {
                    ItemStack item = head.getItem();
                    ItemFactoryRegistry.get().giveItem((Player) ctx.event().getWhoClicked(), plugin.getCfg().getOmit(), item);
                    plugin.getLocalization().sendMessage(player, "purchase.noEconomy",
                            msg -> msg.replaceText(builder -> builder
                                            .matchLiteral("{amount}").replacement(String.valueOf(item.getAmount())))
                                    .replaceText(builder -> builder
                                            .matchLiteral("{name}").replacement(head.getName())));
                    Compatibility.playSound(player, plugin.getSoundConfig().get("head.take"));
                }
            }));
        }

        for (ItemStack item : items) {
            addButton(new SimpleButton(item, ctx -> {
                Player player = (Player) ctx.event().getWhoClicked();
                ClickType click = ctx.event().getClick();

                if (!player.hasPermission("headdb.category.local")) {
                    plugin.getLocalization().sendMessage(player, "noPermission");
                    Compatibility.playSound(player, plugin.getSoundConfig().get("noPermission"));
                    return;
                }

                PlayerData playerData = plugin.getPlayerStorage().getPlayer(player.getUniqueId());
                UUID id = ItemFactoryRegistry.get().getIdFromItem(item);

                if (click == ClickType.RIGHT) {
                    Component itemName = ItemFactoryRegistry.get().getNameFromItem(item);
                    if (playerData.getLocalFavorites().contains(id)) {
                        playerData.removeLocalFavorite(id);
                        plugin.getLocalization().sendMessage(player, "menu.favorites.remove", msg -> msg.replaceText(builder -> builder.matchLiteral("{name}").replacement(itemName)));
                        Compatibility.playSound(player, plugin.getSoundConfig().get("favorite.remove"));
                    } else {
                        playerData.addLocalFavorite(id);
                        plugin.getLocalization().sendMessage(player, "menu.favorites.add", msg -> msg.replaceText(builder -> builder.matchLiteral("{name}").replacement(itemName)));
                        Compatibility.playSound(player, plugin.getSoundConfig().get("favorite.add"));
                    }
                    return;
                }

                ItemFactoryRegistry.get().giveItem(player, plugin.getCfg().getOmit(), item);
                Compatibility.playSound(player, plugin.getSoundConfig().get("head.take"));
            }));
        }

        reRender();
    }
}