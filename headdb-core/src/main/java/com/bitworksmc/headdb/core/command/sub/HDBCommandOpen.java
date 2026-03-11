package com.bitworksmc.headdb.core.command.sub;

import com.bitworksmc.headdb.core.HeadDB;
import com.bitworksmc.headdb.core.command.HDBSubCommand;
import com.bitworksmc.headdb.core.menu.gui.HeadsGUI;
import com.bitworksmc.headdb.core.util.Compatibility;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class HDBCommandOpen extends HDBSubCommand {

    private final HeadDB plugin;
    private List<String> completions;

    public HDBCommandOpen(HeadDB plugin) {
        super("open", "Open the database.", "[category]", "o");
        this.plugin = plugin;
    }

    @Override
    public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getLocalization().sendMessage(sender, "noConsole");
            return;
        }

        if (args.length == 1) {
            this.plugin.getMenuManager().getMainMenu().open(player);
            plugin.getLocalization().sendMessage(sender, "command.open.opening", msg -> msg.replaceText(builder -> builder.matchLiteral("{category}").replacement("Main")));
            Compatibility.playSound(player, plugin.getSoundConfig().get("menu.open"));
            return;
        }

        String category = String.join(" ", String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        HeadsGUI categoryGui = plugin.getMenuManager().get(category.replace(" ", "_").replace("&", "_"));
        if (categoryGui == null) {
            plugin.getLocalization().sendMessage(sender, "command.open.invalidCategory", msg -> msg.replaceText(builder -> builder.matchLiteral("{category}").replacement(category)));
            Compatibility.playSound(player, plugin.getSoundConfig().get("failed"));
            return;
        }

        int pageIndex = 0;
        if (plugin.getCfg().isTrackPage()) {
            pageIndex = categoryGui.getGuiRegistry().getCurrentPage(player.getUniqueId(), categoryGui.getKey()).orElse(0);
        }
        categoryGui.open(player, pageIndex);
        plugin.getLocalization().sendMessage(sender, "command.open.opening", msg -> msg.replaceText(builder -> builder.matchLiteral("{category}").replacement(category)));
        Compatibility.playSound(player, plugin.getSoundConfig().get("menu.open"));
    }

    @Override
    public @Nullable List<String> handleCompletions(CommandSender sender, String[] args) {
        if (completions == null) {
            completions = plugin.getHeadApi().findKnownCategories();
        }
        return completions;
    }

}
