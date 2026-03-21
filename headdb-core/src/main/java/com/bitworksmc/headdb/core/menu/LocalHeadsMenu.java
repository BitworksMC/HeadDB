package com.bitworksmc.headdb.core.menu;

import com.github.thesilentpro.grim.button.SimpleButton;
import com.github.thesilentpro.grim.gui.GUI;
import com.github.thesilentpro.grim.page.PaginatedSimplePage;
import com.bitworksmc.headdb.core.HeadDB;
import com.bitworksmc.headdb.core.factory.ItemFactoryRegistry;
import com.bitworksmc.headdb.core.storage.PlayerData;
import com.bitworksmc.headdb.core.util.Compatibility;
import com.bitworksmc.headdb.core.util.PermissionUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public class LocalHeadsMenu extends PaginatedSimplePage {

    public LocalHeadsMenu(HeadDB plugin, GUI<Integer> gui, Component title, List<ItemStack> items) {
        super(gui, title, 6, 48, 49, 50);
        preventInteraction();

        for (ItemStack item : items) {
            addButton(new SimpleButton(item, ctx -> {
                if (!PermissionUtil.hasCategoryPermission(ctx.event().getWhoClicked(), "local")) {
                    plugin.getLocalization().sendMessage(ctx.event().getWhoClicked(), "noPermission");
                    Compatibility.playSound((Player) ctx.event().getWhoClicked(), plugin.getSoundConfig().get("noPermission"));
                    return;
                }

                if (ctx.event().getClick() == ClickType.RIGHT) {
                    PlayerData playerData = plugin.getPlayerStorage().getPlayer(ctx.event().getWhoClicked().getUniqueId());
                    UUID id = ItemFactoryRegistry.get().getIdFromItem(item);
                    if (playerData.getLocalFavorites().contains(id)) {
                        playerData.removeLocalFavorite(id);
                        plugin.getLocalization().sendMessage(ctx.event().getWhoClicked(), "menu.favorites.remove", msg -> msg.replaceText(builder -> builder.matchLiteral("{name}").replacement(ItemFactoryRegistry.get().getNameFromItem(item))));
                        Compatibility.playSound((Player) ctx.event().getWhoClicked(), plugin.getSoundConfig().get("favorite.remove"));
                    } else {
                        playerData.addLocalFavorite(id);
                        plugin.getLocalization().sendMessage(ctx.event().getWhoClicked(), "menu.favorites.add", msg -> msg.replaceText(builder -> builder.matchLiteral("{name}").replacement(ItemFactoryRegistry.get().getNameFromItem(item))));
                        Compatibility.playSound((Player) ctx.event().getWhoClicked(), plugin.getSoundConfig().get("favorite.add"));
                    }
                    //reRender();
                    return;
                }

                ItemFactoryRegistry.get().giveItem((Player) ctx.event().getWhoClicked(), plugin.getCfg().getOmit(), item);
                Compatibility.playSound((Player) ctx.event().getWhoClicked(), plugin.getSoundConfig().get("head.take"));
            }));
        }
        reRender();
    }

}
