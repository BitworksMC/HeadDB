package com.bitworksmc.headdb.core.command.sub;

import com.bitworksmc.headdb.api.model.Head;
import com.bitworksmc.headdb.core.HeadDB;
import com.bitworksmc.headdb.core.command.HDBSubCommand;
import com.bitworksmc.headdb.core.factory.ItemFactoryRegistry;
import com.bitworksmc.headdb.core.util.Compatibility;
import com.bitworksmc.headdb.core.util.WebsiteLinks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class HDBCommandInspect extends HDBSubCommand {
    private final HeadDB plugin;

    public HDBCommandInspect(HeadDB plugin) {
        super("inspect", "Inspect a held or identified HeadDB head.", "[head]", "head");
        this.plugin = plugin;
    }

    @Override public void handle(CommandSender sender, String[] args) {
        CompletableFuture<Optional<Head>> lookup;
        if (args.length > 1) {
            String identifier = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            lookup = resolve(identifier);
        } else if (sender instanceof Player player) {
            Integer id = ItemFactoryRegistry.get().getHeadIdFromItem(player.getInventory().getItemInMainHand());
            if (id == null) {
                plugin.getLocalization().sendMessage(sender, "command.inspect.notHead");
                return;
            }
            lookup = plugin.getHeadApi().findById(id);
        } else {
            plugin.getLocalization().sendMessage(sender, "command.inspect.consoleUsage");
            return;
        }

        lookup.thenAcceptAsync(result -> {
            if (result.isEmpty()) plugin.getLocalization().sendMessage(sender, "command.inspect.notFound");
            else sendHeadDetails(plugin, sender, result.get());
        }, Compatibility.getSenderExecutor(plugin, sender));
    }

    private CompletableFuture<Optional<Head>> resolve(String identifier) {
        String numeric = identifier.toLowerCase().startsWith("id:") ? identifier.substring(3) : identifier;
        try {
            return plugin.getHeadApi().findById(Integer.parseInt(numeric));
        } catch (NumberFormatException ignored) {
            return plugin.getHeadApi().findByTexture(identifier).thenCompose(found -> found.isPresent()
                    ? CompletableFuture.completedFuture(found)
                    : plugin.getHeadApi().findByName(identifier, false));
        }
    }

    public static void sendHeadDetails(HeadDB plugin, CommandSender sender, Head head) {
        String url = WebsiteLinks.headUrl(plugin.getCfg().getWebsiteUrl(), head.getId());
        Component message = Component.empty()
                .append(Component.text(head.getName() + " #" + head.getId(), NamedTextColor.GOLD)).appendNewline()
                .append(Component.text(" Category: ", NamedTextColor.GRAY)).append(Component.text(head.getCategory(), NamedTextColor.WHITE)).appendNewline()
                .append(Component.text(" Tags: ", NamedTextColor.GRAY)).append(Component.text(String.join(", ", head.getTags()), NamedTextColor.WHITE)).appendNewline()
                .append(Component.text(" Give: ", NamedTextColor.GRAY)).append(Component.text("/hdb give id:" + head.getId(), NamedTextColor.GREEN)).appendNewline()
                .append(WebsiteLinks.makeClickable(Component.text(" View, copy, or report on headdb.net", NamedTextColor.AQUA),
                        url, Component.text("Open " + url, NamedTextColor.AQUA)));
        Compatibility.sendMessage(sender, message);
    }
}
