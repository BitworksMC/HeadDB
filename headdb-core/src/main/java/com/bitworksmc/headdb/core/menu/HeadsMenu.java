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

public class HeadsMenu extends PaginatedSimplePage {

    public HeadsMenu(HeadDB plugin, GUI<Integer> gui, Component title, List<Head> heads) {
        super(gui, title, 6, 48, 49, 50);
        preventInteraction();
        for (Head head : heads) {
            addButton(new SimpleButton(head.getItem(), ctx -> {
                Player player = (Player) ctx.event().getWhoClicked();
                if (ctx.event().getClick() == ClickType.DROP) {
                    // TODO: Manage head
                    return;
                }
                if (ctx.event().getClick() == ClickType.RIGHT) {
                    PlayerData playerData = plugin.getPlayerStorage().getPlayer(ctx.event().getWhoClicked().getUniqueId());
                    if (playerData.getFavorites().contains(head.getId())) {
                        playerData.removeFavorite(head.getId());
                        plugin.getLocalization().sendMessage(ctx.event().getWhoClicked(), "menu.favorites.remove", msg -> msg.replaceText(builder -> builder.matchLiteral("{name}").replacement(head.getName())));
                        Compatibility.playSound((Player) ctx.event().getWhoClicked(), plugin.getSoundConfig().get("favorite.remove"));
                    } else {
                        playerData.addFavorite(head.getId());
                        plugin.getLocalization().sendMessage(ctx.event().getWhoClicked(), "menu.favorites.add", msg -> msg.replaceText(builder -> builder.matchLiteral("{name}").replacement(head.getName())));
                        Compatibility.playSound((Player) ctx.event().getWhoClicked(), plugin.getSoundConfig().get("favorite.add"));
                    }
                    return;
                }

                if (plugin.getEconomyProvider() != null) {
                    new PurchaseHeadMenu(plugin, player, head, this).open(player);
                    Compatibility.playSound((Player) ctx.event().getWhoClicked(), plugin.getSoundConfig().get("menu.open"));
                } else {
                    ItemStack item = head.getItem();
                    ItemFactoryRegistry.get().giveItem((Player) ctx.event().getWhoClicked(), plugin.getCfg().getOmit(), item);
                    plugin.getLocalization().sendMessage(ctx.event().getWhoClicked(), "purchase.noEconomy", msg -> msg.replaceText(builder -> builder.matchLiteral("{amount}").replacement(String.valueOf(item.getAmount()))).replaceText(builder -> builder.matchLiteral("{name}").replacement(head.getName())));
                    Compatibility.playSound((Player) ctx.event().getWhoClicked(), plugin.getSoundConfig().get("head.take"));
                }
            }));
        }
        reRender();
    }

}