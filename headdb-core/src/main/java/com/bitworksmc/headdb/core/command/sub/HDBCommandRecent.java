package com.bitworksmc.headdb.core.command.sub;

import com.bitworksmc.headdb.api.search.SearchQuery;
import com.bitworksmc.headdb.api.search.SearchSort;
import com.bitworksmc.headdb.core.HeadDB;
import com.bitworksmc.headdb.core.command.HDBSubCommand;
import com.bitworksmc.headdb.core.menu.gui.HeadsGUI;
import com.bitworksmc.headdb.core.util.Compatibility;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class HDBCommandRecent extends HDBSubCommand {
    private final HeadDB plugin;

    public HDBCommandRecent(HeadDB plugin) {
        super("recent", "Browse recently assigned HeadDB IDs.", "[amount]", "new");
        this.plugin = plugin;
    }

    @Override public void handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getLocalization().sendMessage(sender, "noConsole");
            return;
        }
        int amount = 100;
        if (args.length > 1) {
            try { amount = Math.min(500, Math.max(1, Integer.parseInt(args[1]))); }
            catch (NumberFormatException ignored) {
                plugin.getLocalization().sendMessage(sender, "invalidNumber", msg -> msg.replaceText(builder ->
                        builder.matchLiteral("{number}").replacement(args[1])));
                return;
            }
        }
        SearchQuery query = SearchQuery.builder().sort(SearchSort.ID).ascending(false).limit(amount).build();
        plugin.getHeadApi().search(query).thenAcceptAsync(page -> {
            HeadsGUI gui = new HeadsGUI(plugin, "recent_" + player.getUniqueId(),
                    plugin.getLocalization().getMessage(player.getUniqueId(), "menu.recent.name")
                            .orElseGet(() -> Component.text("HeadDB » Recently added")), page.getItems());
            gui.open(player);
            Compatibility.playSound(player, plugin.getSoundConfig().get("menu.open"));
        }, Compatibility.getEntityExecutor(plugin, player));
    }
}
