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
        super("give", "Give a specific head to yourself or another player.", "<head> [amount/head] [head]", "g");
        this.plugin = plugin;
        plugin.getHeadApi().onReady().thenAccept(heads -> this.headNameCompletions = heads.stream()
                .map(Head::getName)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList());
    }

    // /hdb give <head>
    // /hdb give <amount> <head>
    // /hdb give <player> <amount> <head>
    @Override
    public void handle(CommandSender sender, String[] args) {
        Player target;
        int amount = 1;
        int identifierStart;
        String amountArgument = "1";

        boolean targetsPlayer = args.length >= 4 && !isInteger(args[1]) && isInteger(args[2]);
        if (targetsPlayer) {
            target = Bukkit.getPlayer(args[1]);
            amountArgument = args[2];
            identifierStart = 3;
        } else {
            if (!(sender instanceof Player player)) {
                plugin.getLocalization().sendMessage(sender, "command.give.consoleUsage");
                return;
            }
            target = player;
            if (args.length >= 3 && isInteger(args[1])) {
                amountArgument = args[1];
                identifierStart = 2;
            } else {
                identifierStart = 1;
            }
        }

        if (target == null) {
            plugin.getLocalization().sendMessage(sender, "invalidTarget", msg -> msg.replaceText(builder -> builder.matchLiteral("{target}").replacement(args[1])));
            Compatibility.playSound(sender, plugin.getSoundConfig().get("failure"));
            return;
        }

        try {
            amount = Integer.parseInt(amountArgument);
        } catch (NumberFormatException nfe) {
            String invalidAmount = amountArgument;
            plugin.getLocalization().sendMessage(sender, "invalidNumber", msg -> msg.replaceText(builder -> builder.matchLiteral("{number}").replacement(invalidAmount)));
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
        String id = String.join(" ", Arrays.copyOfRange(args, identifierStart, args.length));
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
        if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            Stream<String> playerNames = Bukkit.getOnlinePlayers().stream().map(Player::getName);
            Stream<String> headNames = headNameCompletions.stream();
            Stream<String> amounts = numberCompletions.stream()
                    .filter(value -> Integer.parseInt(value) <= plugin.getCfg().getMaxBuyAmount());
            return Stream.concat(Stream.concat(playerNames, headNames), amounts)
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .distinct()
                    .limit(100)
                    .toList();
        }

        Player explicitTarget = Bukkit.getPlayerExact(args[1]);
        if (explicitTarget != null && args.length == 3) {
            int maximum = plugin.getCfg().getMaxBuyAmount();
            return numberCompletions.stream()
                    .filter(value -> Integer.parseInt(value) <= maximum)
                    .toList();
        }

        int identifierStart = explicitTarget != null ? 3 : (isInteger(args[1]) ? 2 : 1);
        if (args.length > identifierStart) {
            String prefix = String.join(" ", Arrays.copyOfRange(args, identifierStart, args.length)).trim().toLowerCase(Locale.ROOT);

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

    private static boolean isInteger(String value) {
        if (value == null || value.isEmpty()) return false;
        int start = value.charAt(0) == '-' ? 1 : 0;
        if (start == value.length()) return false;
        for (int i = start; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) return false;
        }
        return true;
    }

}
