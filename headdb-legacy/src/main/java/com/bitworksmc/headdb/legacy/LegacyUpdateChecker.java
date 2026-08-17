package com.bitworksmc.headdb.legacy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class LegacyUpdateChecker implements Listener {
    private static final String RELEASE_URL = "https://api.github.com/repos/BitworksMC/HeadDB/releases/latest";
    private final LegacyHeadDB plugin;
    private volatile String latest;

    LegacyUpdateChecker(LegacyHeadDB plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        long hours = Math.max(1L, plugin.getConfig().getLong("updateChecker.intervalHours", 24L));
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::check, 1L, hours * 60L * 60L * 20L);
    }

    private void check() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(RELEASE_URL).openConnection();
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "HeadDB-Legacy");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            reader.close();
            String tag = json.get("tag_name").getAsString();
            if (newer(tag, plugin.getDescription().getVersion())) {
                latest = tag;
                if (plugin.getConfig().getBoolean("updateChecker.notifyConsole", true)) {
                    plugin.getLogger().info("A new HeadDB version is available: " + tag);
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().fine("Update check failed: " + exception.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (latest != null && plugin.getConfig().getBoolean("updateChecker.notifyPlayers", true)
                && player.hasPermission("headdb.update.notify")) {
            player.sendMessage(ChatColor.YELLOW + "A new HeadDB version is available: " + ChatColor.GREEN + latest);
            player.sendMessage(ChatColor.GRAY + "https://github.com/BitworksMC/HeadDB/releases/latest");
        }
    }

    static boolean newer(String candidate, String current) {
        int[] left = numbers(candidate);
        int[] right = numbers(current);
        for (int i = 0; i < 3; i++) {
            if (left[i] != right[i]) return left[i] > right[i];
        }
        return false;
    }

    private static int[] numbers(String value) {
        String clean = value == null ? "" : value.replaceFirst("^[vV]", "");
        String[] parts = clean.split("[-+]")[0].split("\\.");
        int[] result = new int[3];
        for (int i = 0; i < Math.min(3, parts.length); i++) {
            try { result[i] = Integer.parseInt(parts[i]); } catch (NumberFormatException ignored) { }
        }
        return result;
    }
}
