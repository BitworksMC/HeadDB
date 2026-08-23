package com.bitworksmc.headdb.core.command.sub;

import com.bitworksmc.headdb.core.HeadDB;
import com.bitworksmc.headdb.core.command.HDBSubCommand;
import com.bitworksmc.headdb.core.util.Compatibility;
import com.bitworksmc.headdb.core.util.WebsiteLinks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Opens HeadDB's public submission page from an in-game link. */
public class HDBCommandSubmit extends HDBSubCommand {

    private final HeadDB plugin;

    public HDBCommandSubmit(HeadDB plugin) {
        super("submit", "Submit a head for review on headdb.net.", null, "submission");
        this.plugin = plugin;
    }

    @Override
    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getLocalization().sendMessage(sender, "noConsole");
            return;
        }

        String url = WebsiteLinks.submissionUrl(plugin.getCfg().getWebsiteUrl());
        Component message = plugin.getLocalization().getMessage(player.getUniqueId(), "command.submit.link")
                .orElseGet(() -> Component.text()
                        .append(Component.text("Have a head to share? ", NamedTextColor.GRAY))
                        .append(Component.text("Submit it on headdb.net", NamedTextColor.AQUA))
                        .append(Component.text(" for review.", NamedTextColor.GRAY))
                        .build());
        Compatibility.sendMessage(player, WebsiteLinks.makeClickable(
                message,
                url,
                Component.text("Open " + url, NamedTextColor.AQUA)
        ));
    }
}
