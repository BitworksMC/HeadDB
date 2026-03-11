package com.bitworksmc.headdb.core.menu;

import com.github.thesilentpro.grim.button.SimpleButton;
import com.github.thesilentpro.grim.gui.GUI;
import com.github.thesilentpro.grim.page.PaginatedSimplePage;
import com.bitworksmc.headdb.core.HeadDB;
import com.bitworksmc.headdb.core.config.CustomCategory;
import com.bitworksmc.headdb.core.util.Compatibility;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

public class CustomCategoriesMenu extends PaginatedSimplePage {

    public CustomCategoriesMenu(HeadDB plugin, GUI<Integer> gui, Component title, List<CustomCategory> categories) {
        super(gui, title, 6, 48, 49, 50);
        preventInteraction();
        for (CustomCategory category : categories) {
            addButton(new SimpleButton(category.getIcon(), ctx -> {
                if (ctx.event().getClick() == ClickType.DROP) {
                    // todo: manage head
                    return;
                }

                if (!category.isEnabled()) {
                    Compatibility.playSound((Player) ctx.event().getWhoClicked(), plugin.getSoundConfig().get("menu.disabled"));
                    return;
                }

                if (!ctx.event().getWhoClicked().hasPermission("headdb.category." + category)) {
                    plugin.getLocalization().sendMessage(ctx.event().getWhoClicked(), "noPermission");
                    Compatibility.playSound((Player) ctx.event().getWhoClicked(), plugin.getSoundConfig().get("noPermission"));
                    return;
                }

                plugin.getMenuManager().get(category.getIdentifier()).open((Player) ctx.event().getWhoClicked());
                Compatibility.playSound((Player) ctx.event().getWhoClicked(), plugin.getSoundConfig().get("menu.open"));
            }));
        }
        reRender();
    }

}