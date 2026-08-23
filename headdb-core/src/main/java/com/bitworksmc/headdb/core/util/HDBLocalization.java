package com.bitworksmc.headdb.core.util;

import com.bitworksmc.headdb.core.HeadDB;
import com.github.thesilentpro.localization.paper.PaperLoader;
import com.github.thesilentpro.localization.paper.lib.AbstractLocalization;
import com.github.thesilentpro.localization.paper.lib.ConsoleLogLevel;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Bukkit-safe localization adapter.
 *
 * <p>The upstream {@code PaperLocalization} convenience class calls Paper's
 * {@code JavaPlugin#getComponentLogger()}. Extending it made the separately
 * shaded Spigot artifact binary-incompatible, so HeadDB uses the library's
 * implementation-neutral core and supplies its own sender adapter.</p>
 */
public class HDBLocalization extends AbstractLocalization<Component, String, UUID> {

    private static final Logger LOGGER = LoggerFactory.getLogger(HDBLocalization.class);
    private static final Pattern DEFAULT_ARGS_PATTERN = Pattern.compile("\\$\\{(?:(\\d+)(\\+)?|(\\*))}", Pattern.CASE_INSENSITIVE);
    private final HeadDB plugin;
    private Pattern argsPattern = DEFAULT_ARGS_PATTERN;

    public HDBLocalization(@NotNull HeadDB plugin) {
        super();
        this.plugin = plugin;
        setConsoleLogFunction((level, message) -> Compatibility.sendMessage(plugin.getServer().getConsoleSender(), message));
    }

    @Override
    public @NotNull Optional<Component> getMessage(@NotNull UUID receiver, @NotNull String key) {
        if (plugin.getPlayerStorage() != null) {
            setLanguage(receiver, plugin.getPlayerStorage().getPlayer(receiver).getLanguage());
        }
        return super.getMessage(receiver, key).map(message -> applyPlaceholders(receiver, message));
    }

    @Override
    public @NotNull Optional<Component> getConsoleMessage(@NotNull String key) {
        return super.getConsoleMessage(key).map(message -> applyPlaceholders(null, message));
    }

    @Override
    public void sendTranslatedMessage(@NotNull UUID receiver, @NotNull Component message) {
        Entity entity = Bukkit.getEntity(receiver);
        if (entity == null) {
            LOGGER.debug("Skipping localized message because receiver {} is no longer online", receiver);
            return;
        }
        if (message instanceof TextComponent textMessage
                && textMessage.content().isBlank()
                && textMessage.children().isEmpty()) {
            return;
        }
        Compatibility.getEntityExecutor(plugin, entity).execute(() -> Compatibility.sendMessage(entity, message));
    }

    @Override
    public void sendMessage(
            @NotNull UUID receiver,
            @NotNull String key,
            @Nullable UnaryOperator<Component> function,
            String... args
    ) {
        notNull(receiver, "Receiver must not be null!");
        notNull(key, "Key must not be null!");
        getMessage(receiver, key).ifPresent(message -> {
            Component resolved = replaceArguments(message, args);
            if (function != null) {
                resolved = function.apply(resolved);
            }
            sendTranslatedMessage(receiver, resolved);
        });
    }

    @Override
    public void sendConsoleMessage(
            @Nullable ConsoleLogLevel level,
            @NotNull String key,
            @Nullable UnaryOperator<Component> function,
            String... args
    ) {
        notNull(key, "Key must not be null!");
        getConsoleMessage(key).ifPresent(message -> {
            Component resolved = replaceArguments(message, args);
            if (function != null) {
                resolved = function.apply(resolved);
            }
            sendTranslatedConsoleMessage(level, resolved);
        });
    }

    public void sendMessage(
            CommandSender receiver,
            String key,
            @Nullable UnaryOperator<Component> function,
            String... args
    ) {
        if (receiver instanceof Player player) {
            sendMessage(player.getUniqueId(), key, function, args);
            return;
        }

        getConsoleMessage(key).ifPresent(message -> {
            Component resolved = replaceArguments(message, args);
            if (function != null) {
                resolved = function.apply(resolved);
            }
            Compatibility.sendMessage(receiver, resolved);
        });
    }

    public void sendMessage(CommandSender receiver, String key, String... args) {
        sendMessage(receiver, key, null, args);
    }

    public void sendMessage(CommandSender receiver, String key, @Nullable UnaryOperator<Component> function) {
        sendMessage(receiver, key, function, (String[]) null);
    }

    public void sendMessage(CommandSender receiver, String key) {
        sendMessage(receiver, key, null, (String[]) null);
    }

    public void sendMessage(String key, CommandSender... receivers) {
        if (receivers == null) {
            return;
        }
        for (CommandSender receiver : receivers) {
            sendMessage(receiver, key);
        }
    }

    public void setArgsPattern(Pattern argsPattern) {
        this.argsPattern = argsPattern != null ? argsPattern : DEFAULT_ARGS_PATTERN;
    }

    public Pattern getArgsPattern() {
        return argsPattern;
    }

    public void init() {
        try {
            loadLanguages(new PaperLoader(HeadDB.class, "messages", new File(plugin.getDataFolder(), "messages")));
            //setConsoleLogFunction((level, message) -> LOGGER.atLevel(toSLF4JLevel(level)).log(ANSIComponentSerializer.ansi().serialize(message)));
        } catch (IOException ex) {
            LOGGER.error("Failed to load languages!", ex);
        }
    }

    public List<String> getAvailableLanguages() {
        return getLanguages().keySet().stream().sorted().toList();
    }

    private Component replaceArguments(Component message, @Nullable String... args) {
        if (args == null) {
            return message;
        }
        if (args.length == 0) {
            return message.replaceText(builder -> builder.match(argsPattern).replacement(Component.empty()));
        }

        return message.replaceText(builder -> builder.match(argsPattern).replacement((matcher, ignored) -> {
            if (matcher.group(3) != null) {
                return Component.text(String.join(" ", args));
            }

            try {
                int index = Integer.parseInt(matcher.group(1)) - 1;
                if (index < 0 || index >= args.length) {
                    return Component.empty();
                }
                if (matcher.group(2) != null) {
                    return Component.text(String.join(" ", java.util.Arrays.copyOfRange(args, index, args.length)));
                }
                return Component.text(args[index] != null ? args[index] : "");
            } catch (NumberFormatException ex) {
                return Component.empty();
            }
        }));
    }

    private Component applyPlaceholders(@Nullable UUID receiver, Component message) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return message;
        }

        String serialized = MiniMessage.miniMessage().serialize(message);
        OfflinePlayer player = receiver == null ? null : Bukkit.getOfflinePlayer(receiver);
        return MiniMessage.miniMessage().deserialize(PlaceholderAPI.setPlaceholders(player, serialized));
    }

}
