package com.bitworksmc.headdb.core.menu;

import com.github.thesilentpro.grim.button.SimpleButton;
import com.github.thesilentpro.grim.gui.GUI;
import com.github.thesilentpro.grim.page.PaginatedSimplePage;
import com.bitworksmc.headdb.api.model.Head;
import com.bitworksmc.headdb.core.HeadDB;
import com.bitworksmc.headdb.core.factory.ItemFactoryRegistry;
import com.bitworksmc.headdb.core.menu.registry.ConcurrentPageRegistry;
import com.bitworksmc.headdb.core.storage.PlayerData;
import com.bitworksmc.headdb.core.util.Compatibility;
import com.bitworksmc.headdb.core.util.PermissionUtil;
import com.bitworksmc.headdb.core.command.sub.HDBCommandInspect;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HeadsMenu extends PaginatedSimplePage {

    private final HeadDB plugin;
    private final List<Head> heads;
    private final @Nullable String permissionCategory;
    private boolean initialized;

    public HeadsMenu(HeadDB plugin, GUI<Integer> gui, Component title, List<Head> heads) {
        this(plugin, gui, title, heads, null);
    }

    public HeadsMenu(HeadDB plugin, GUI<Integer> gui, Component title, List<Head> heads, @Nullable String permissionCategory) {
        super(ConcurrentPageRegistry.INSTANCE, gui, title, 6, 48, 49, 50);
        this.plugin = plugin;
        this.heads = List.copyOf(heads);
        this.permissionCategory = permissionCategory;
        preventInteraction();

        // Head items are relatively expensive to create. Categories can contain
        // thousands of pages, so populate only the page a player actually opens.
        this.initialized = false;
    }

    @Override
    public InventoryView open(Player player, boolean render) {
        initializeButtons();
        return super.open(player, render);
    }

    private synchronized void initializeButtons() {
        if (initialized) {
            return;
        }

        int slot = 0;
        for (Head head : heads) {
            setButton(slot++, new SimpleButton(head.getItem(), ctx -> {
                Player player = (Player) ctx.event().getWhoClicked();
                if (ctx.event().getClick() == ClickType.DROP) {
                    HDBCommandInspect.sendHeadDetails(plugin, ctx.event().getWhoClicked(), head);
                    return;
                }
                String requiredCategory = permissionCategory != null ? permissionCategory : head.getCategory();
                if (!PermissionUtil.hasCategoryPermission(player, requiredCategory)) {
                    plugin.getLocalization().sendMessage(player, "noPermission");
                    Compatibility.playSound(player, plugin.getSoundConfig().get("noPermission"));
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
                    new PurchaseHeadMenu(plugin, player, head, this, requiredCategory).open(player);
                    Compatibility.playSound((Player) ctx.event().getWhoClicked(), plugin.getSoundConfig().get("menu.open"));
                } else {
                    ItemStack item = head.getItem();
                    ItemFactoryRegistry.get().giveItem((Player) ctx.event().getWhoClicked(), plugin.getCfg().getOmit(), item);
                    plugin.getLocalization().sendMessage(ctx.event().getWhoClicked(), "purchase.noEconomy", msg -> msg.replaceText(builder -> builder.matchLiteral("{amount}").replacement(String.valueOf(item.getAmount()))).replaceText(builder -> builder.matchLiteral("{name}").replacement(head.getName())));
                    Compatibility.playSound((Player) ctx.event().getWhoClicked(), plugin.getSoundConfig().get("head.take"));
                }
            }));
        }
        initialized = true;
    }

}
