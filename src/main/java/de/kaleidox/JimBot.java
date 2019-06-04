package de.kaleidox;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import de.kaleidox.javacord.util.commands.CommandHandler;
import de.kaleidox.javacord.util.server.properties.ServerPropertiesManager;
import de.kaleidox.javacord.util.ui.embed.DefaultEmbedFactory;
import de.kaleidox.jim.AssignmentManager;
import de.kaleidox.jim.commands.AdminCommands;
import de.kaleidox.jim.commands.BasicCommands;
import de.kaleidox.jim.commands.JimCommands;
import de.kaleidox.util.files.FileProvider;

import org.apache.logging.log4j.Logger;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.activity.ActivityType;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.user.UserStatus;
import org.javacord.api.util.logging.ExceptionLogger;
import org.javacord.core.util.logging.LoggerUtil;

public final class JimBot {
    public static final Logger LOG = LoggerUtil.getLogger(JimBot.class);
    public static final DiscordApi API;
    public static final CommandHandler CMD;
    public static final ServerPropertiesManager PROP;

    static {
        try {
            File file = FileProvider.getFile("login/token.cred");
            System.out.println("Looking for token file at " + file.getAbsolutePath());
            API = new DiscordApiBuilder()
                    .setToken(new BufferedReader(new FileReader(file)).readLine())
                    .login()
                    .exceptionally(ExceptionLogger.get())
                    .join();

            API.updateStatus(UserStatus.DO_NOT_DISTURB);
            API.updateActivity("Booting up...");

            DefaultEmbedFactory.setEmbedSupplier(() -> new EmbedBuilder().setColor(new Color(0x7289DA)));

            CMD = new CommandHandler(API);
            CMD.prefixes = new String[]{"jim@"};
            CMD.useDefaultHelp(null);
            CMD.registerCommands(BasicCommands.INSTANCE);
            CMD.registerCommands(JimCommands.INSTANCE);
            CMD.registerCommands(AdminCommands.INSTANCE);

            PROP = new ServerPropertiesManager(FileProvider.getFile("data/properties.json"));
            PROP.usePropertyCommand(null, CMD);
            PROP.register("bot.prefix", CMD.prefixes[0])
                    .setDisplayName("Custom Command Prefix")
                    .setDescription("A custom prefix to call bot commands with");
            PROP.register("bot.commandchannel", -1)
                    .setDisplayName("Command Channel ID")
                    .setDescription("The ID of the only channel where the commands should be executed.\n" +
                            "If the ID is invalid, every channel is accepted.");

            CMD.useCustomPrefixes(PROP.getProperty("bot.prefix"));
            CMD.useCommandChannel(PROP.getProperty("bot.commandchannel"));
            CMD.useBotMentionAsPrefix = true;

            API.getThreadPool()
                    .getScheduler()
                    .scheduleAtFixedRate(() -> {
                        try {
                            PROP.storeData();
                            AssignmentManager.INSTANCE.storeData();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }, 5, 5, TimeUnit.MINUTES);
            Runtime.getRuntime().addShutdownHook(new Thread(JimBot::shutdown));

            AssignmentManager.INSTANCE.init();

            API.updateStatus(UserStatus.ONLINE);
            API.updateActivity(ActivityType.LISTENING, CMD.prefixes[0] + "help");
        } catch (Exception e) {
            throw new RuntimeException("Error in initializer", e);
        }
    }

    public static void main(String[] args) {
        API.addListener(AssignmentManager.INSTANCE);
    }

    private static void shutdown() {
        try {
            AssignmentManager.INSTANCE.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
