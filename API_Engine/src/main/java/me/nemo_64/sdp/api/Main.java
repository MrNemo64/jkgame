package me.nemo_64.sdp.api;

import me.nemo_64.sdp.api.rest.GameHttpServer;
import me.nemo_64.sdp.utilities.NetworkUtil;
import me.nemo_64.sdp.utilities.NumberUtil;
import me.nemo_64.sdp.utilities.configuration.Config;
import me.nemo_64.sdp.utilities.configuration.ConfigurationBuilder;
import me.nemo_64.sdp.utilities.player.PlayerManager;
import me.nemo_64.sdp.utilities.player.PlayerUtil;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static me.nemo_64.sdp.api.util.ConfigurationEntry.*;

public class Main {

    public static final Logger API_ENGINE_LOGGER = Logger.getLogger("API_Engine");

    static {
        API_ENGINE_LOGGER.setUseParentHandlers(false);
        try {
            Path file = Paths.get("log/API_Engine." + (System.currentTimeMillis() / 1000) + ".log");
            Path parent = file.getParent();
            if (!Files.exists(parent))
                Files.createDirectories(parent);
            Files.createFile(file);
            FileHandler handler = new FileHandler(file.toString());
            handler.setFormatter(new SimpleFormatter());
            API_ENGINE_LOGGER.addHandler(handler);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        PlayerManager.setLoggerParent(API_ENGINE_LOGGER);
        PlayerUtil.setLoggerParent(API_ENGINE_LOGGER);
    }

    public static void main(String[] args) {
        if (args.length != 0 && args.length != 1) {
            System.out.println("Invalid amount of arguments");
            showUsage();
            System.exit(-1);
        }
        var confBuilder = ConfigurationBuilder.newBuilder()
                .registerPrimitiveParsers()
                .registerParser(HttpClient.Version.class, NetworkUtil::httpVersionParser)
                .register(GAME_HTTP_SERVER_IP, String.class, false, "localhost")
                .register(GAME_HTTP_SERVER_PORT, Integer.class, false, 5999, NetworkUtil::isValidPort)
                .register(NO_GAME_FOUND_MESSAGE, String.class, false, "No game found")
                .register(MAP_FILE, String.class, false, "latestGame.json")
                .register(CACHE_TIME, Integer.class, false, 20, NumberUtil::isGraterThanZero)
                .register(UNSUPPORTED_METHOD_MESSAGE, String.class, false)
                .withLogger(API_ENGINE_LOGGER);
        if (args.length == 1) {
            confBuilder.withFile(Path.of(args[0]));
        }
        var conf = confBuilder.create();
        if (conf.isError()) {
            System.out.println("Could not load config: " + conf.error().message());
            System.exit(-1);
        }
        Config.setInstance(conf.value());

        API_ENGINE_LOGGER.info("Using the following configuration:" + Config.getInstance().display("  "));
        try {
            GameHttpServer server = new GameHttpServer();
        } catch (IOException e) {
            API_ENGINE_LOGGER.warning("IO exception while starting rest api: " + e.getMessage());
        }

    }

    private static void showUsage() {
        System.out.println("API_Engine [config]");
    }

}
