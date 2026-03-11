package com.bitworksmc.headdb.core.util;

import com.bitworksmc.headdb.core.factory.ItemFactoryRegistry;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("deprecation")
public class Compatibility {

    public static final boolean IS_PAPER;
    public static final boolean IS_FOLIA;

    static {
        boolean isPaper;
        boolean isFolia;
        try {
            Class.forName("com.destroystokyo.paper.profile.PlayerProfile");
            isPaper = true;
        } catch (ClassNotFoundException e) {
            isPaper = false;
        }
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }
        IS_PAPER = isPaper;
        IS_FOLIA = isFolia;
    }

    public static Executor getMainThreadExecutor(JavaPlugin plugin) {
        if (plugin == null) {
            throw new RuntimeException("Plugin instance is null!");
        }
        if (IS_FOLIA) {
            return r -> plugin.getServer().getGlobalRegionScheduler().execute(plugin, r);
        } else if (IS_PAPER) {
            return plugin.getServer().getScheduler().getMainThreadExecutor(plugin);
        } else {
            return r -> plugin.getServer().getScheduler().runTask(plugin, r);
        }
    }

    public static Executor getEntityExecutor(JavaPlugin plugin, Entity entity) {
        if (plugin == null) {
            throw new RuntimeException("Plugin instance is null!");
        }
        if (entity == null) {
            throw new RuntimeException("Entity instance is null!");
        }

        if (!IS_FOLIA) {
            return getMainThreadExecutor(plugin);
        }

        return r -> {
            if (!entity.isValid()) {
                return;
            }
            if (entity instanceof Player player && !player.isOnline()) {
                return;
            }

            entity.getScheduler().execute(plugin, r, () -> { }, 1L);
        };
    }

    public static Executor getSenderExecutor(JavaPlugin plugin, CommandSender sender) {
        if (sender instanceof Entity entity) {
            return getEntityExecutor(plugin, entity);
        }
        return getMainThreadExecutor(plugin);
    }

    public static void runAsyncRepeating(JavaPlugin plugin, Runnable task, long initialDelayTicks, long periodTicks) {
        if (plugin == null) {
            throw new RuntimeException("Plugin instance is null!");
        }
        if (task == null) {
            return;
        }

        if (IS_FOLIA) {
            plugin.getServer().getAsyncScheduler().runAtFixedRate(
                    plugin,
                    scheduledTask -> task.run(),
                    ticksToMillis(initialDelayTicks),
                    ticksToMillis(periodTicks),
                    TimeUnit.MILLISECONDS
            );
            return;
        }

        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, task, initialDelayTicks, periodTicks);
    }

    private static long ticksToMillis(long ticks) {
        if (ticks <= 0L) {
            return 0L;
        }
        return ticks * 50L;
    }

    public static String getPluginVersion(JavaPlugin plugin) {
        if (plugin == null) {
            throw new RuntimeException("Plugin instance is null!");
        }
        if (IS_PAPER) {
            return plugin.getPluginMeta().getVersion();
        } else {
            return plugin.getDescription().getVersion();
        }
    }

    public static void sendMessage(CommandSender sender, Component component) {
        if (sender == null) {
            return;
        }
        if (component == null) {
            return; // Silently fail on null components to avoid exceptions
        }
        if (IS_PAPER) {
            sender.sendMessage(component);
        } else {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', LegacyComponentSerializer.legacyAmpersand().serialize(component)));
        }
    }

    public static void playSound(Player player, Sound sound) {
        if (sound == null) {
            return;
        }
        if (IS_PAPER) {
            player.playSound(sound);
        } else {
            player.playSound(player, sound.name().value(), sound.volume(), sound.pitch());
        }
    }

    public static void playSound(CommandSender sender, Sound sound) {
        if (!(sender instanceof Player)) {
            return;
        }
        playSound((Player) sender, sound);
    }

    // TODO: Refactor usages of the below methods to use item factory instead of delegating to it.

    public static ItemStack setItemDetails(ItemStack item, Component name, @NotNull Component @Nullable ... lore) {
        return ItemFactoryRegistry.get().setItemDetails(item, name, lore);
    }

    public static ItemStack setItemDetails(ItemStack item, Component name) {
        return setItemDetails(item, name, (Component[]) null);
    }

    public static ItemStack newItem(Material material) {
        return ItemFactoryRegistry.get().newItem(material);
    }

    public static ItemStack newItem(Material material, Component name, Component... lore) {
        return ItemFactoryRegistry.get().newItem(material, name, lore);
    }
    
}
