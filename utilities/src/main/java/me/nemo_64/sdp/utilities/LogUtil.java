package me.nemo_64.sdp.utilities;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class LogUtil {

    public static void logToFile(Logger logger, Path file) {
        logger.setUseParentHandlers(false);
        try {
            Files.createFile(file);
            FileHandler handler = new FileHandler(file.toString());
            handler.setFormatter(new SimpleFormatter());
            logger.addHandler(handler);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
