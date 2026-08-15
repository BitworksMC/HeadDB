package com.bitworksmc.headdb.core.command.sub;

import com.bitworksmc.headdb.core.HeadDB;
import com.bitworksmc.headdb.core.command.HDBSubCommand;
import com.bitworksmc.headdb.core.storage.PlayerData;
import com.bitworksmc.headdb.core.util.Compatibility;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Lets players opt out of HeadDB's interface sounds. */
public class HDBCommandSounds extends HDBSubCommand {

    private final HeadDB plugin;

    public HDBCommandSounds(HeadDB plugin) {
        super("sounds", "Toggle HeadDB interface sounds.", null, "sound");
        this.plugin = plugin;
    }

    @Override
    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getLocalization().sendMessage(sender, "noConsole");
            return;
        }

        PlayerData data = plugin.getPlayerStorage().getPlayer(player.getUniqueId());
        boolean enabled = !data.isSoundEnabled();
        data.setSoundEnabled(enabled);
        plugin.getLocalization().sendMessage(player, enabled ? "command.sounds.enabled" : "command.sounds.disabled");
        if (enabled) {
            Compatibility.playSound(player, plugin.getSoundConfig().get("success"));
        }
    }
}
