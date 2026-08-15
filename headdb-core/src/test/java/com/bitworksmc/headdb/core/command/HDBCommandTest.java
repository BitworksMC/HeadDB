package com.bitworksmc.headdb.core.command;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class HDBCommandTest {

    @Test
    void usageCountIncludesTheSubcommandArgument() {
        assertEquals(4, HDBMainCommand.minimumArgumentCount("<player> <amount> <head>"));
        assertEquals(1, HDBMainCommand.minimumArgumentCount("[category]"));
        assertEquals(2, HDBMainCommand.minimumArgumentCount("<required> [optional]"));
    }

    @Test
    void subcommandsAndAliasesAreCaseInsensitive() {
        HDBSubCommandManager manager = new HDBSubCommandManager(null);
        HDBSubCommand command = new HDBSubCommand("Give", "", null, "G") {
            @Override
            public void handle(CommandSender sender, String[] args) {
            }
        };

        manager.register(command);

        assertSame(command, manager.get("GIVE"));
        assertSame(command, manager.get("g"));
    }
}
