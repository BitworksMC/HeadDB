package com.bitworksmc.headdb.core.command;

import com.bitworksmc.headdb.core.HeadDB;
import com.bitworksmc.headdb.core.command.sub.HDBCommandGive;
import com.bitworksmc.headdb.core.command.sub.HDBCommandInfo;
import com.bitworksmc.headdb.core.command.sub.HDBCommandOpen;
import com.bitworksmc.headdb.core.command.sub.HDBCommandSearch;
import com.bitworksmc.headdb.core.command.sub.HDBCommandSounds;
import com.bitworksmc.headdb.core.command.sub.HDBCommandSubmit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HDBSubCommandManager {

    private final Map<String, HDBSubCommand> commands = new HashMap<>();
    private final List<String> realNames = new ArrayList<>();

    private final HeadDB plugin;

    public HDBSubCommandManager(HeadDB plugin) {
        this.plugin = plugin;
    }

    public void registerDefaults() {
        register(new HDBCommandInfo(plugin));
        register(new HDBCommandGive(plugin));
        register(new HDBCommandSearch(plugin));
        register(new HDBCommandOpen(plugin));
        register(new HDBCommandSounds(plugin));
        register(new HDBCommandSubmit(plugin));
    }

    public void register(HDBSubCommand command) {
        this.commands.put(command.getName().toLowerCase(Locale.ROOT), command);
        this.realNames.add(command.getName());
        for (String alias : command.getAliases()) {
            this.commands.put(alias.toLowerCase(Locale.ROOT), command);
        }
    }

    public HDBSubCommand get(String name) {
        return name == null ? null : this.commands.get(name.toLowerCase(Locale.ROOT));
    }

    public Map<String, HDBSubCommand> getCommands() {
        return commands;
    }

    public List<String> getRealNames() {
        return List.copyOf(realNames);
    }

}
