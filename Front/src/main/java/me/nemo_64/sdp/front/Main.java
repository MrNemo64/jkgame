package me.nemo_64.sdp.front;

import me.nemo_64.sdp.utilities.NetworkUtil;
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

import static me.nemo_64.sdp.front.ConfigurationEntry.*;

public class Main {

    public static final Logger FRONT_LOGGER = Logger.getLogger("Front");

    static {
        FRONT_LOGGER.setUseParentHandlers(false);
        try {
            Path file = Paths.get("log/Front." + (System.currentTimeMillis() / 1000) + ".log");
            Path parent = file.getParent();
            if (!Files.exists(parent))
                Files.createDirectories(parent);
            Files.createFile(file);
            FileHandler handler = new FileHandler(file.toString());
            handler.setFormatter(new SimpleFormatter());
            FRONT_LOGGER.addHandler(handler);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        PlayerManager.setLoggerParent(FRONT_LOGGER);
        PlayerUtil.setLoggerParent(FRONT_LOGGER);
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
                .register(SPECTATE_HTTP_SERVER_IP, String.class, false)
                .register(SPECTATE_HTTP_SERVER_PORT, Integer.class, false, -1, NetworkUtil::isValidPort) // TODO poner
                                                                                                         // puerto
                .register(REQUEST_PLAYER_URL, String.class, false)
                .register(DEFAULT_MAP_REQUEST_URL, String.class, false)
                .register(FOOD_IMAGE_URL, String.class, false)
                .register(MINE_IMAGE_URL, String.class, false)
                .register(NPC_IMAGE_URL, String.class, false)
                .register(PLAYER_IMAGE_URL, String.class, false)
                .withLogger(FRONT_LOGGER);
        if (args.length == 1) {
            confBuilder.withFile(Path.of(args[0]));
        }
        var conf = confBuilder.create();
        if (conf.isError()) {
            System.out.println("Could not load config: " + conf.error().message());
            System.exit(-1);
        }
        Config.setInstance(conf.value());

        FRONT_LOGGER.info("Using the following configuration:" + Config.getInstance().display("  "));
        try {
            SpectateHttpServer server = new SpectateHttpServer();
        } catch (IOException e) {
            // TODO
            throw new RuntimeException(e);
        }

    }

    private static void showUsage() {
        System.out.println("Front [config]");
    }

}
