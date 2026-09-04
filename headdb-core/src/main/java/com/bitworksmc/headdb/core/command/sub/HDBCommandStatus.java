package com.bitworksmc.headdb.core.command.sub;

import com.bitworksmc.headdb.api.catalog.CatalogStatus;
import com.bitworksmc.headdb.core.HeadDB;
import com.bitworksmc.headdb.core.command.HDBSubCommand;
import com.bitworksmc.headdb.core.util.Compatibility;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.time.Duration;
import java.time.Instant;

public final class HDBCommandStatus extends HDBSubCommand {
    private final HeadDB plugin;

    public HDBCommandStatus(HeadDB plugin) {
        super("status", "Show catalog synchronization health.", null);
        this.plugin = plugin;
    }

    @Override public void handle(CommandSender sender, String[] args) {
        CatalogStatus status = plugin.getHeadApi().getCatalogStatus();
        Component message = Component.empty()
                .append(Component.text("HeadDB catalog", NamedTextColor.GOLD)).appendNewline()
                .append(Component.text(" State: ", NamedTextColor.GRAY))
                .append(Component.text(status.isReady() ? "ready" : "loading", status.isReady() ? NamedTextColor.GREEN : NamedTextColor.YELLOW)).appendNewline()
                .append(Component.text(" Heads: ", NamedTextColor.GRAY)).append(Component.text(status.getHeadCount(), NamedTextColor.WHITE)).appendNewline()
                .append(Component.text(" Revision: ", NamedTextColor.GRAY)).append(Component.text(status.getRevision() < 0 ? "legacy/full snapshot" : String.valueOf(status.getRevision()), NamedTextColor.WHITE)).appendNewline()
                .append(Component.text(" Last success: ", NamedTextColor.GRAY)).append(Component.text(age(status.getLastSuccessfulUpdateEpochMillis()), NamedTextColor.WHITE)).appendNewline()
                .append(Component.text(" Source: ", NamedTextColor.GRAY)).append(Component.text(status.getSource() == null ? "not selected" : status.getSource(), NamedTextColor.WHITE))
                .append(status.getLastError() == null ? Component.empty() : Component.newline()
                        .append(Component.text(" Last error: ", NamedTextColor.RED)).append(Component.text(status.getLastError(), NamedTextColor.WHITE)));
        Compatibility.sendMessage(sender, message);
    }

    private static String age(long epochMillis) {
        if (epochMillis <= 0) return "never";
        Duration elapsed = Duration.between(Instant.ofEpochMilli(epochMillis), Instant.now());
        if (elapsed.toMinutes() < 1) return "just now";
        if (elapsed.toHours() < 1) return elapsed.toMinutes() + "m ago";
        if (elapsed.toDays() < 1) return elapsed.toHours() + "h ago";
        return elapsed.toDays() + "d ago";
    }
}
