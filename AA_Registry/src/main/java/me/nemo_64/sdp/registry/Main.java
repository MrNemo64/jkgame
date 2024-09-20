package me.nemo_64.sdp.registry;

import me.nemo_64.sdp.registry.rest.PlayerHttpsServer;
import me.nemo_64.sdp.utilities.NetworkUtil;
import me.nemo_64.sdp.utilities.NumberUtil;
import me.nemo_64.sdp.utilities.configuration.Config;
import me.nemo_64.sdp.utilities.configuration.ConfigurationBuilder;
import me.nemo_64.sdp.utilities.player.PlayerManager;
import me.nemo_64.sdp.utilities.player.PlayerUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static me.nemo_64.sdp.registry.util.ConfigurationEntry.*;

public class Main {

    public static final ExecutorService REGISTRY_EXECUTORS = Executors.newFixedThreadPool(2);

    public static final Logger REGISTRY_LOGGER = Logger.getLogger("AA_Registry");

    static {
        // REGISTRY_LOGGER.setUseParentHandlers(false);
        try {
            Path file = Paths.get("log/AA_Registry." + (System.currentTimeMillis() / 1000) + ".log");
            Path parent = file.getParent();
            if (!Files.exists(parent))
                Files.createDirectories(parent);
            Files.createFile(file);
            FileHandler handler = new FileHandler(file.toString());
            handler.setFormatter(new SimpleFormatter());
            REGISTRY_LOGGER.addHandler(handler);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        PlayerManager.setLoggerParent(REGISTRY_LOGGER);
        PlayerUtil.setLoggerParent(REGISTRY_LOGGER);
    }

    public static void main(String[] args) {
        if (args.length != 0 && args.length != 1) {
            showUsage();
            System.exit(-1);
        }
        var confBuilder = ConfigurationBuilder.newBuilder()
                .registerPrimitiveParsers()
                .register(SOCKET_PORT, Integer.class, false, 6001, NetworkUtil::isValidPort)
                .register(HTTPS_PORT, Integer.class, false, 6000, NetworkUtil::isValidPort)
                .register(HTTPS_IP, String.class, false, "localhost")
                .register(PLAYERS_FOLDER, String.class, false, "players")
                .register(ALLOWED_CHARACTERS_FILE, String.class, true)
                .register(ATTEMPTS_PER_REQUEST, Integer.class, false, 3, NumberUtil::isGraterThanZero)
                .register(TIME_OUT, Integer.class, false, 2000, NumberUtil::isGraterThanZero)
                .register(CERTIFICATE_FILE_PASSWORD, String.class, false, "nemo_64-toor")
                .register(CERTIFICATE_FILE, String.class, false, "https_certificate.jks")
                .register(HTTPS_SSL_PROTOCOL, String.class, false, "TLS")
                .register(KEY_STORE_TYPE, String.class, false, "JKS")
                .register(KEY_FACTORY_ALGORITHM, String.class, false, "SunX509")
                .register(TRUST_FACTORY_ALGORITHM, String.class, false, "SunX509")
                .register(PLAYER_NOT_SPECIFIED_RESPONSE, String.class, false, "Missing player alias")
                .register(PLAYER_NOT_FOUND_RESPONSE, String.class, false, "Could not find '%player_alias%'")
                .register(REQUEST_BODY_INVALID, String.class, false)
                .register(ALIAS_NOT_AVAILABLE_RESPONSE, String.class, false)
                .register(PLAYER_CREATE_ERROR_RESPONSE, String.class, false)
                .register(POST_MISSING_FILED_RESPONSE, String.class, false)
                .register(INCORRECT_PASSWORD_POST_RESPONSE, String.class, false)
                .withLogger(REGISTRY_LOGGER);
        if (args.length == 1) {
            Path file = Path.of(args[0]).toAbsolutePath();
            REGISTRY_LOGGER.info("Using " + file + " as configuration");
            confBuilder.withFile(file);
        }
        var conf = confBuilder.create();
        if (conf.isError()) {
            System.out.println("Could not load config: " + conf.error().message());
            System.exit(-1);
        }
        Config.setInstance(conf.value());

        REGISTRY_LOGGER.info("Using the following configuration:" + Config.getInstance().display("  "));

        var playerManager = PlayerManager.setUp(Config.getPath(PLAYERS_FOLDER),
                Config.getOptionalPath(ALLOWED_CHARACTERS_FILE).orElse(null));
        if (playerManager.isError()) {
            System.out.println(switch (playerManager.error()) {
                case GENERIC_IO -> "A generic IO exception occurred while setting up the player manager";
                case COULD_NOT_SET_UP_PLAYERS_FOLDER -> "Could not set up the players data folder";
            } + ". Check the logs for more information.");
            System.exit(-1);
        }
        PlayerHttpsServer restHttpServer = new PlayerHttpsServer(playerManager.value());
        var registryResult = RegistryService.createOnPort(Config.getInt(SOCKET_PORT),
                Config.getInt(ATTEMPTS_PER_REQUEST), Config.getInt(TIME_OUT), playerManager.value());
        if (registryResult.isError()) {
            System.out.println(switch (registryResult.error()) {
                case IO_EXCEPTION -> "A generic IO exception occurred while setting up the registry service";
                case SECURITY_MANAGER_DOES_NOT_ALLOW ->
                    "Could not set up the registry, there is a security manager in place";
                case ILLEGAL_PORT -> "Illegal port given";
            } + ". Check the logs for more information.");
            System.exit(-1);
        }
        registryResult.value().startService();
    }

    private static void showUsage() {
        System.out.println("AA_Registry <config>");
    }

}
