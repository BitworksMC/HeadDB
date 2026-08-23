package com.bitworksmc.headdb.core.command.sub;

import com.bitworksmc.headdb.core.HeadDB;
import com.bitworksmc.headdb.core.command.HDBSubCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class HDBCommandLanguage extends HDBSubCommand {
    private final HeadDB plugin;

    public HDBCommandLanguage(HeadDB plugin) {
        super("language", "Choose your HeadDB language.", "[language]", "lang", "locale");
        this.plugin = plugin;
    }

    @Override public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getLocalization().sendMessage(sender, "noConsole");
            return;
        }
        List<String> available = plugin.getLocalization().getAvailableLanguages();
        if (args.length == 1) {
            plugin.getLocalization().sendMessage(sender, "command.language.available", msg -> msg.replaceText(builder ->
                    builder.matchLiteral("{languages}").replacement(String.join(", ", available))));
            return;
        }
        String requested = args[1].toLowerCase(Locale.ROOT);
        if (!available.contains(requested)) {
            plugin.getLocalization().sendMessage(sender, "command.language.invalid", msg -> msg.replaceText(builder ->
                    builder.matchLiteral("{language}").replacement(requested)));
            return;
        }
        plugin.getPlayerStorage().getPlayer(player.getUniqueId()).setLanguage(requested);
        plugin.getLocalization().setLanguage(player.getUniqueId(), requested);
        plugin.getLocalization().sendMessage(sender, "command.language.changed", msg -> msg.replaceText(builder ->
                builder.matchLiteral("{language}").replacement(requested)));
    }

    @Override public List<String> handleCompletions(CommandSender sender, String[] args) {
        return plugin.getLocalization().getAvailableLanguages();
    }
}
