package de.kaleidox.jim.commands;

import de.kaleidox.javacord.util.commands.Command;
import de.kaleidox.javacord.util.commands.CommandGroup;
import de.kaleidox.javacord.util.ui.embed.DefaultEmbedFactory;

import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.User;

@CommandGroup(name = "Basic Commands", description = "All commands for basic interaction with the bot")
public enum BasicCommands {
    INSTANCE;

    @Command(usage = "about", description = "Who made this bot?", ordinal = 0)
    public EmbedBuilder about(User user) {
        return DefaultEmbedFactory.create()
                .addField("Bot Author", "[Kaleidox#0001](http://kaleidox.de)")
                .addField("GitHub Repository", "http://github.com/burdoto/Jim-Bot [GPL 3.0 License]")
                .setFooter("Made for 1ceSpark#0004");
    }
}
