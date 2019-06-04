package de.kaleidox.util.files;

import java.io.File;
import java.io.IOException;

public class FileProvider {
    private final static String UNIX_PREPATH = ""; //"/var/bots/jim-bot/";

    public static File getFile(String subPath) {
        File file = new File((OSValidator.isUnix() ? UNIX_PREPATH : "") + subPath);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return file;
    }
}
