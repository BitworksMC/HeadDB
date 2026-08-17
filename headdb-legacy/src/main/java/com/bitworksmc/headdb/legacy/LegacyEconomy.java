package com.bitworksmc.headdb.legacy;

import com.bitworksmc.headdb.api.model.Head;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;

import java.lang.reflect.Method;

final class LegacyEconomy {
    private final Object provider;
    private final FileConfiguration config;

    LegacyEconomy(FileConfiguration config) {
        this.config = config;
        this.provider = findProvider(config.getString("economy.provider", "NONE"));
    }

    boolean isEnabled() { return provider != null; }

    double price(Head head) {
        String headPath = "economy.cost.head." + head.getId();
        if (config.contains(headPath)) return Math.max(0D, config.getDouble(headPath));
        return Math.max(0D, config.getDouble("economy.cost.category." + head.getCategory().toLowerCase(), 0D));
    }

    boolean purchase(OfflinePlayer player, double amount) {
        if (provider == null || amount <= 0D) return true;
        try {
            Object has = invokePlayer("has", player, amount);
            if (!(has instanceof Boolean) || !((Boolean) has)) return false;
            Object response = invokePlayer("withdrawPlayer", player, amount);
            if (response == null) return false;
            try {
                Object success = response.getClass().getMethod("transactionSuccess").invoke(response);
                return success instanceof Boolean && (Boolean) success;
            } catch (NoSuchMethodException ignored) {
                return true;
            }
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    private Object invokePlayer(String name, OfflinePlayer player, double amount) throws ReflectiveOperationException {
        for (Method method : provider.getClass().getMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (!method.getName().equals(name) || types.length != 2 || types[1] != double.class) continue;
            if (types[0].isInstance(player)) return method.invoke(provider, player, amount);
            if (types[0] == String.class) return method.invoke(provider, player.getName(), amount);
        }
        throw new NoSuchMethodException(name);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object findProvider(String configured) {
        if (configured == null || !configured.equalsIgnoreCase("VAULT")) return null;
        try {
            Class economy = Class.forName("net.milkbowl.vault.economy.Economy");
            org.bukkit.plugin.RegisteredServiceProvider registration = Bukkit.getServicesManager().getRegistration(economy);
            return registration == null ? null : registration.getProvider();
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
