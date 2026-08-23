package com.bitworksmc.headdb.core.command.sub;

import com.bitworksmc.headdb.core.HeadDB;
import com.bitworksmc.headdb.core.command.HDBSubCommand;
import org.bukkit.command.CommandSender;

public final class HDBCommandReload extends HDBSubCommand {
    private final HeadDB plugin;

    public HDBCommandReload(HeadDB plugin) {
        super("reload", "Reload messages, sounds, menus, prices, and local categories.", null);
        this.plugin = plugin;
    }

    @Override public void handle(CommandSender sender, String[] args) {
        plugin.reloadRuntimeConfiguration();
        plugin.getLocalization().sendMessage(sender, "command.reload.success");
    }
}
