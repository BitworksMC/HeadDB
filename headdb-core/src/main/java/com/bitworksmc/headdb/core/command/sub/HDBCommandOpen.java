package com.bitworksmc.headdb.core.command.sub;

import com.bitworksmc.headdb.core.HeadDB;
import com.bitworksmc.headdb.core.command.HDBSubCommand;
import com.bitworksmc.headdb.core.menu.gui.HeadsGUI;
import com.bitworksmc.headdb.core.util.Compatibility;
import com.bitworksmc.headdb.core.util.PermissionUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class HDBCommandOpen extends HDBSubCommand {

    private final HeadDB plugin;

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

        if (!plugin.getHeadApi().isReady()) {
            plugin.getLocalization().sendMessage(sender, "databaseLoading");
            Compatibility.playSound(player, plugin.getSoundConfig().get("menu.none"));
            return;
        }

        if (args.length == 1) {
            this.plugin.getMenuManager().getMainMenu().open(player);
            plugin.getLocalization().sendMessage(sender, "command.open.opening", msg -> msg.replaceText(builder -> builder.matchLiteral("{category}").replacement("Main")));
            Compatibility.playSound(player, plugin.getSoundConfig().get("menu.open"));
            return;
        }

        String category = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        HeadsGUI categoryGui = plugin.getMenuManager().get(category);
        if (categoryGui == null) {
            plugin.getLocalization().sendMessage(sender, "command.open.invalidCategory", msg -> msg.replaceText(builder -> builder.matchLiteral("{category}").replacement(category)));
            Compatibility.playSound(player, plugin.getSoundConfig().get("failure"));
            return;
        }
        if (!PermissionUtil.hasCategoryPermission(player, category)) {
            plugin.getLocalization().sendMessage(player, "noPermission");
            Compatibility.playSound(player, plugin.getSoundConfig().get("noPermission"));
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
        String prefix = args.length <= 1
                ? ""
                : String.join(" ", Arrays.copyOfRange(args, 1, args.length)).toLowerCase(Locale.ROOT);
        return plugin.getMenuManager().getCategoryNames().stream()
                .filter(category -> PermissionUtil.hasCategoryPermission(sender, category))
                .filter(category -> category.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }

}
