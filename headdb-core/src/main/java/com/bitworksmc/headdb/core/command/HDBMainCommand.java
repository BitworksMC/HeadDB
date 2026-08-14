package com.bitworksmc.headdb.core.command;

import com.bitworksmc.headdb.core.HeadDB;
import com.bitworksmc.headdb.core.util.Compatibility;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class HDBMainCommand implements CommandExecutor, TabCompleter {

    private static final Pattern USAGE_PATTERN = Pattern.compile("\\s+");
    private final HeadDB plugin;

    public HDBMainCommand(HeadDB plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                plugin.getLocalization().sendMessage(sender, "noConsole");
                return true;
            }
            if (!(sender.hasPermission("headdb.command.open"))) {
                plugin.getLocalization().sendMessage(sender, "noPermission");
                Compatibility.playSound(player, plugin.getSoundConfig().get("noPermission"));
                return true;
            }
            if (!plugin.getHeadApi().isReady()) {
                plugin.getLocalization().sendMessage(sender, "databaseLoading");
                Compatibility.playSound(player, plugin.getSoundConfig().get("menu.none"));
                return true;
            }
            plugin.getMenuManager().getMainMenu().open(player);
            Compatibility.playSound(player, plugin.getSoundConfig().get("menu.open"));
            plugin.getLocalization().sendMessage(sender, "command.open.opening", msg -> msg.replaceText(builder -> builder.matchLiteral("{category}").replacement("Main")));
            return true;
        }

        String sub = args[0];
        HDBSubCommand subCommand = plugin.getSubCommandManager().get(sub);
        if (subCommand == null) {
            plugin.getLocalization().sendMessage(sender, "invalidSubCommand", args);
            return true;
        }

        if (!sender.hasPermission("headdb.command." + subCommand.getName())) {
            plugin.getLocalization().sendMessage(sender, "noPermission");
            Compatibility.playSound(sender, plugin.getSoundConfig().get("noPermission"));
            return true;
        }

        // Validate usage format
        if (subCommand.getUsage() != null && !subCommand.getUsage().isBlank()) {
            if (args.length < minimumArgumentCount(subCommand.getUsage())) {
                plugin.getLocalization().sendMessage(sender, "commandUsage", msg -> msg.replaceText(builder -> builder.matchLiteral("{usage}").replacement("/" + label + " " + sub + " " + subCommand.getUsage())));
                return true;
            }
        }

        subCommand.handle(sender, args);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> completions = new ArrayList<>();
            for (String name : plugin.getSubCommandManager().getRealNames()) {
                HDBSubCommand subCommand = plugin.getSubCommandManager().get(name);
                if (name.toLowerCase(Locale.ROOT).startsWith(prefix)
                        && sender.hasPermission("headdb.command." + subCommand.getName())) {
                    completions.add(name);
                }
            }
            return completions;
        }

        HDBSubCommand subCommand = plugin.getSubCommandManager().get(args[0]);
        if (subCommand == null) {
            return List.of();
        }

        if (!sender.hasPermission("headdb.command." + subCommand.getName())) {
            return List.of();
        }

        return subCommand.handleCompletions(sender, args);
    }

    static int minimumArgumentCount(String usage) {
        // args[0] is the sub-command itself; every non-[optional] usage token
        // adds one required argument after it.
        int required = 1;
        if (usage == null || usage.isBlank()) {
            return required;
        }
        for (String part : USAGE_PATTERN.split(usage.trim())) {
            if (!(part.startsWith("[") && part.endsWith("]"))) {
                required++;
            }
        }
        return required;
    }

}
