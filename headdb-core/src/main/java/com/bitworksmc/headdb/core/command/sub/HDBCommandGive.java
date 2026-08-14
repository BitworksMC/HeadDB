package com.bitworksmc.headdb.core.command.sub;

import com.bitworksmc.headdb.api.model.Head;
import com.bitworksmc.headdb.core.HeadDB;
import com.bitworksmc.headdb.core.command.HDBSubCommand;
import com.bitworksmc.headdb.core.factory.ItemFactoryRegistry;
import com.bitworksmc.headdb.core.util.Compatibility;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

public class HDBCommandGive extends HDBSubCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(HDBCommandGive.class);
    private final HeadDB plugin;
    private volatile List<String> headNameCompletions = List.of();

    public HDBCommandGive(HeadDB plugin) {
        super("give", "Give a specific head to a player.", "<player> <amount> <head>", "g");
        this.plugin = plugin;
        plugin.getHeadApi().onReady().thenAccept(heads -> this.headNameCompletions = heads.stream()
                .map(Head::getName)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList());
    }

    // /hdb give <player> <amount> <head>
    @Override
    public void handle(CommandSender sender, String[] args) {
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            plugin.getLocalization().sendMessage(sender, "invalidTarget", msg -> msg.replaceText(builder -> builder.matchLiteral("{target}").replacement(args[1])));
            Compatibility.playSound(sender, plugin.getSoundConfig().get("failure"));
            return;
        }

        int amount = 1;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException nfe) {
            plugin.getLocalization().sendMessage(sender, "invalidNumber", msg -> msg.replaceText(builder -> builder.matchLiteral("{number}").replacement(args[2])));
            Compatibility.playSound(sender, plugin.getSoundConfig().get("failure"));
            return;
        }

        if (amount < 1 || amount > plugin.getCfg().getMaxBuyAmount()) {
            plugin.getLocalization().sendMessage(sender, "command.give.invalidAmount", msg -> msg.replaceText(builder ->
                    builder.matchLiteral("{max}").replacement(String.valueOf(plugin.getCfg().getMaxBuyAmount()))));
            Compatibility.playSound(sender, plugin.getSoundConfig().get("failure"));
            return;
        }

        final int fAmount = amount;
        String id = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        plugin.getHeadApi().onReady()
                .thenCompose(ignored -> plugin.getHeadApi().findByName(id, true))
                .thenCompose(optionalHead -> {
                    if (optionalHead.isPresent()) {
                        return CompletableFuture.completedFuture(optionalHead.get());
                    } else if (id.startsWith("id:")) {
                        try {
                            int numericId = Integer.parseInt(id.substring(3));
                            return plugin.getHeadApi().findById(numericId).thenApply(optional -> optional.orElse(null));
                        } catch (NumberFormatException e) {
                            return CompletableFuture.completedFuture(null);
                        }
                    } else {
                        return plugin.getHeadApi().findByTexture(id).thenApply(optional -> optional.orElse(null));
                    }
                })
                .thenAccept(head -> {
                    if (head == null) {
                        Compatibility.getSenderExecutor(plugin, sender).execute(() -> {
                            plugin.getLocalization().sendMessage(sender, "command.give.invalidId", msg -> msg.replaceText(builder -> builder.matchLiteral("{id}").replacement(id)));
                            Compatibility.playSound(sender, plugin.getSoundConfig().get("failure"));
                        });
                        return;
                    }
                    Compatibility.getEntityExecutor(plugin, target).execute(() -> {
                        ItemStack item = head.getItem();
                        item.setAmount(fAmount);
                        ItemFactoryRegistry.get().giveItem(target, plugin.getCfg().getOmit(), item);
                        Compatibility.getSenderExecutor(plugin, sender).execute(() -> {
                            plugin.getLocalization().sendMessage(sender, "command.give.success", msg ->
                                    msg.replaceText(builder -> builder.matchLiteral("{amount}").replacement(String.valueOf(fAmount)))
                                            .replaceText(builder -> builder.matchLiteral("{name}").replacement(head.getName()))
                                            .replaceText(builder -> builder.matchLiteral("{target}").replacement(target.getName()))
                            );
                            Compatibility.playSound(sender, plugin.getSoundConfig().get("success"));
                        });
                    });
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to give head '{}' to {}", id, target.getName(), ex);
                    Compatibility.getSenderExecutor(plugin, sender).execute(() -> {
                        plugin.getLocalization().sendMessage(sender, "command.give.failed");
                        Compatibility.playSound(sender, plugin.getSoundConfig().get("failure"));
                    });
                    return null;
                });
    }

    private static final List<String> numberCompletions = List.of("1", "32", "64");

    @Override
    public @Nullable List<String> handleCompletions(CommandSender sender, String[] args) {
        if (args.length == 3) {
            int maximum = plugin.getCfg().getMaxBuyAmount();
            return numberCompletions.stream()
                    .filter(value -> Integer.parseInt(value) <= maximum)
                    .toList();
        }

        if (args.length >= 4) {
            String prefix = String.join(" ", Arrays.copyOfRange(args, 3, args.length)).trim().toLowerCase(Locale.ROOT);

            Stream<String> heads = headNameCompletions.stream();

            if (prefix.isEmpty()) {
                return heads.limit(100).toList();
            }

            return heads
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .limit(100)
                    .toList();
        }

        return null;
    }

}
