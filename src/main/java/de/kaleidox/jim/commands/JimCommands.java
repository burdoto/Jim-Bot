package de.kaleidox.jim.commands;

import java.awt.Color;
import java.text.ParseException;
import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

import de.kaleidox.JimBot;
import de.kaleidox.javacord.util.commands.Command;
import de.kaleidox.javacord.util.commands.CommandGroup;
import de.kaleidox.javacord.util.ui.embed.DefaultEmbedFactory;
import de.kaleidox.javacord.util.ui.reactions.InfoReaction;
import de.kaleidox.jim.AssignmentManager;

import me.xdrop.fuzzywuzzy.FuzzySearch;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.permission.RoleBuilder;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;

@CommandGroup(name = "Main Commands", description = "Main Jim functionality", ordinal = 0)
public enum JimCommands {
    INSTANCE;

    @Command(
            description = "Temporarily assign a role to a user",
            usage = "tempRole <User> <Rolename or Mention> <Time>",
            enablePrivateChat = false,
            ordinal = 0,
            requiredUserMentions = 1,
            requiredArguments = 3,
            async = true
    )
    public InfoReaction tempRole(Server server, String[] args, Command.Parameters param) {
        User target = param.getUserMentions().get(0);
        Role targetRole = param.getRoleMentions().stream()
                .findAny()
                .orElseGet(() -> server.getRoles()
                        .stream()
                        .max(Comparator.comparingInt(role -> FuzzySearch.ratio(args[1], role.getName())))
                        .orElse(null));

        if (targetRole == null) {
            return new InfoReaction(
                    param.getCommandMessage(),
                    "❌",
                    "Could not determine role from input: `" + args[1] + "`",
                    30,
                    TimeUnit.SECONDS,
                    DefaultEmbedFactory.INSTANCE
            );
        }

        server.addRoleToUser(target, targetRole).join();

        Instant removeStamp;
        try {
            removeStamp = AssignmentManager.INSTANCE.removeRoleAfter(target, targetRole, args[2], false);
        } catch (ParseException e) {
            JimBot.LOG.catching(e);
            return new InfoReaction(
                    param.getCommandMessage(),
                    InfoReaction.MessageTypeEmoji.WARNING,
                    e.getMessage(),
                    30,
                    TimeUnit.SECONDS,
                    DefaultEmbedFactory.INSTANCE
            );
        }

        if (removeStamp != null) return new InfoReaction(
                param.getCommandMessage(),
                "✅",
                "Role added to user until " + removeStamp.toString() + "!",
                30,
                TimeUnit.SECONDS,
                DefaultEmbedFactory.INSTANCE
        );
        else return new InfoReaction(
                param.getCommandMessage(),
                "❌",
                "Something went wrong. Please contact the developer!",
                30,
                TimeUnit.SECONDS,
                () -> DefaultEmbedFactory.INSTANCE.get().setColor(Color.RED)
        );
    }

    @Command(
            description = "Create a role and temporarily assign it to a user",
            usage = "tempRole <User> <Rolename> <Time> [Role hex code]",
            enablePrivateChat = false,
            ordinal = 0,
            requiredUserMentions = 1,
            requiredArguments = 3,
            async = true
    )
    public InfoReaction assignNew(Server server, String[] args, Command.Parameters param) {
        User target = param.getUserMentions().get(0);

        RoleBuilder roleBuilder = server.createRoleBuilder()
                .setName(args[1]);

        if (args.length >= 4) {
            Color color = new Color(Integer.decode("0x"+args[3]));

            roleBuilder.setColor(color);
        }

        Role targetRole = roleBuilder.create()
                .join();

        if (targetRole == null) {
            return new InfoReaction(
                    param.getCommandMessage(),
                    "❌",
                    "Could not determine role from input: `" + args[1] + "`",
                    30,
                    TimeUnit.SECONDS,
                    DefaultEmbedFactory.INSTANCE
            );
        }

        server.addRoleToUser(target, targetRole)
                .join();

        Instant removeStamp;
        try {
            removeStamp = AssignmentManager.INSTANCE.removeRoleAfter(target, targetRole, args[2], true);
        } catch (ParseException e) {
            JimBot.LOG.catching(e);
            return new InfoReaction(
                    param.getCommandMessage(),
                    InfoReaction.MessageTypeEmoji.WARNING,
                    e.getMessage(),
                    30,
                    TimeUnit.SECONDS,
                    DefaultEmbedFactory.INSTANCE
            );
        }

        if (removeStamp != null) return new InfoReaction(
                param.getCommandMessage(),
                "✅",
                "Role added to user until " + removeStamp.toString() + "!",
                30,
                TimeUnit.SECONDS,
                DefaultEmbedFactory.INSTANCE
        );
        else return new InfoReaction(
                param.getCommandMessage(),
                "❌",
                "Something went wrong. Please contact the developer!",
                30,
                TimeUnit.SECONDS,
                () -> DefaultEmbedFactory.INSTANCE.get().setColor(Color.RED)
        );
    }
}
