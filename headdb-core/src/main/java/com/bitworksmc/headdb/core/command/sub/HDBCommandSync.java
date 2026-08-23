package com.bitworksmc.headdb.core.command.sub;

import com.bitworksmc.headdb.core.HeadDB;
import com.bitworksmc.headdb.core.command.HDBSubCommand;
import com.bitworksmc.headdb.core.util.Compatibility;
import org.bukkit.command.CommandSender;

public final class HDBCommandSync extends HDBSubCommand {
    private final HeadDB plugin;

    public HDBCommandSync(HeadDB plugin) {
        super("sync", "Synchronize the managed catalog now.", null, "refresh");
        this.plugin = plugin;
    }

    @Override public void handle(CommandSender sender, String[] args) {
        plugin.getLocalization().sendMessage(sender, "command.sync.start");
        plugin.synchronizeCatalog().whenComplete((heads, failure) ->
                Compatibility.getSenderExecutor(plugin, sender).execute(() -> {
                    if (failure != null) {
                        plugin.getLocalization().sendMessage(sender, "command.sync.failed", msg -> msg.replaceText(builder ->
                                builder.matchLiteral("{error}").replacement(rootMessage(failure))));
                    } else {
                        plugin.getLocalization().sendMessage(sender, "command.sync.success", msg -> msg.replaceText(builder ->
                                builder.matchLiteral("{amount}").replacement(String.valueOf(heads.size()))));
                    }
                }));
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
