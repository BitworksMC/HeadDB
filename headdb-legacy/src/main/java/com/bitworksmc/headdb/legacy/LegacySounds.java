package com.bitworksmc.headdb.legacy;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

import java.io.File;

final class LegacySounds {
    private final YamlConfiguration sounds;
    private final LegacyPlayerStorage storage;

    LegacySounds(File dataFolder, LegacyPlayerStorage storage) {
        this.sounds = YamlConfiguration.loadConfiguration(new File(dataFolder, "sounds.yml"));
        this.storage = storage;
    }

    void play(Player player, String key) {
        if (player == null || !storage.get(player.getUniqueId()).isSoundsEnabled()) return;
        String sound = sounds.getString(key + ".sound");
        if (sound == null || sound.trim().isEmpty()) return;
        if (Bukkit.getBukkitVersion().startsWith("1.8")) sound = sound18(sound);
        float volume = (float) sounds.getDouble(key + ".volume", 1.0D);
        float pitch = (float) sounds.getDouble(key + ".pitch", 1.0D);
        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
            // Sound identifiers changed repeatedly; an unknown configured sound is non-fatal.
        }
    }

    private static String sound18(String sound) {
        if (sound.equalsIgnoreCase("block.anvil.land")) return "random.anvil_land";
        if (sound.equalsIgnoreCase("entity.player.levelup")) return "random.levelup";
        if (sound.equalsIgnoreCase("block.lever.click")) return "random.click";
        if (sound.equalsIgnoreCase("entity.arrow.hit.player")) return "random.successful_hit";
        if (sound.equalsIgnoreCase("entity.bat.takeoff")) return "mob.bat.takeoff";
        if (sound.equalsIgnoreCase("entity.villager.no")) return "mob.villager.no";
        if (sound.equalsIgnoreCase("block.wooden_button.click_off")) return "random.click";
        if (sound.equalsIgnoreCase("block.note_block.pling")) return "note.pling";
        return sound;
    }
}
