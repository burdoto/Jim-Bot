package de.kaleidox.jim.commands;

import de.kaleidox.javacord.util.commands.Command;
import de.kaleidox.javacord.util.commands.CommandGroup;

import org.javacord.api.entity.user.User;

@CommandGroup
public enum AdminCommands {
    INSTANCE;

    @Command(shownInHelpCommand = false)
    public void shutdown(User user) {
        if (user != null && user.isBotOwner())
            System.exit(0);
    }
}
