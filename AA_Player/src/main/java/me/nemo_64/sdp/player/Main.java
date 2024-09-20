package me.nemo_64.sdp.player;

import me.nemo_64.sdp.player.rest.RESTCreateAccountManager;
import me.nemo_64.sdp.player.rest.RESTModifyAccountManager;
import me.nemo_64.sdp.player.socket.SocketJoinMatchManager;
import me.nemo_64.sdp.player.util.ConfigurationEntry;
import me.nemo_64.sdp.player.util.ConsoleUtil;
import me.nemo_64.sdp.utilities.NetworkUtil;
import me.nemo_64.sdp.utilities.NumberUtil;
import me.nemo_64.sdp.utilities.configuration.Config;
import me.nemo_64.sdp.utilities.configuration.ConfigurationBuilder;
import me.nemo_64.sdp.utilities.secure.SSLFix;
import me.nemo_64.sdp.utilities.secure.SecurityConfigFilters;
import me.nemo_64.sdp.utilities.secure.SymmetricCipher;

import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static me.nemo_64.sdp.player.util.ConfigurationEntry.*;

public class Main {

    public static final Logger PLAYER_LOGGER = Logger.getLogger("AA_Player");

    static {
        PLAYER_LOGGER.setUseParentHandlers(false);
        try {
            Path file = Paths.get("log/AA_Player." + (System.currentTimeMillis() / 1000) + ".log");
            Path parent = file.getParent();
            if (!Files.exists(parent))
                Files.createDirectories(parent);
            Files.createFile(file);
            FileHandler handler = new FileHandler(file.toString());
            handler.setFormatter(new SimpleFormatter());
            PLAYER_LOGGER.addHandler(handler);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Logger kafkaLogger = Logger.getLogger("org.apache.kafka");
        kafkaLogger.setLevel(Level.OFF);
    }

    public static void main(String[] args) {
        SSLFix.execute();
        System.setProperty("com.sun.net.ssl.checkRevocation", "false");
        if (args.length != 1 && args.length != 3) {
            showUsage();
            System.exit(-1);
        }
        var confBuilder = ConfigurationBuilder.newBuilder()
                .registerPrimitiveParsers()
                .registerParser(HttpClient.Version.class, NetworkUtil::httpVersionParser)
                .register(ENGINE_IP, String.class, false)
                .register(ENGINE_PORT, Integer.class, false, 6002, NetworkUtil::isValidPort)
                .register(REGISTRY_SOCKET_IP, String.class, false)
                .register(REGISTRY_SOCKET_PORT, Integer.class, false, 6001, NetworkUtil::isValidPort)
                .register(BOOTSTRAP_IP, String.class, false)
                .register(KAFKA_ENCRYPTION_ALGORITHM, String.class, false, "AES",
                        SecurityConfigFilters::isValidCipherAlgorithm)
                .register(REST_REQUEST_TIMEOUT, Integer.class, false, 3000, NumberUtil::isGraterThanZero)
                .register(REST_REQUEST_HTTP_VERSION, HttpClient.Version.class, false, HttpClient.Version.HTTP_1_1)
                .register(REGISTRY_HTTP_IP, String.class, false)
                .withLogger(PLAYER_LOGGER);
        if (args.length >= 1) {
            confBuilder.withFile(Path.of(args[0]));
        }
        var conf = confBuilder.create();
        if (conf.isError()) {
            System.out.println("Could not load config: " + conf.error().message());
            System.exit(-1);
        }
        Config.setInstance(conf.value());

        PLAYER_LOGGER.info("Using the following configuration:" + Config.getInstance().display("  "));

        if (args.length == 3) {
            System.out.println("Reconnecting with token " + args[1] + " and password " + args[2]);
            SymmetricCipher symmetricCipher;
            try {
                symmetricCipher = SymmetricCipher.create(args[2]);
            } catch (NoSuchPaddingException | NoSuchAlgorithmException e) {
                PLAYER_LOGGER.warning(
                        "A theoretically impossible exception was thrown: " + e.getClass() + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
            OngoingGame game = new OngoingGame(args[1], Config.getString(BOOTSTRAP_IP), symmetricCipher);
            game.play(true);
            game.waitUntilFinish();
        }

        while (true) {
            showMenu();
            int option = ConsoleUtil.readInt((str) -> {
                System.out.printf("%s is not an option%n", str);
                showMenu();
            });
            if (1 <= option && option <= 4) {
                switch (option) {
                    case 1 ->
                        new RESTCreateAccountManager().createNewAccount(Config.getString(REGISTRY_SOCKET_IP),
                                Config.getInt(REGISTRY_SOCKET_PORT));
                    case 2 ->
                        new RESTModifyAccountManager().modifyAccount(Config.getString(REGISTRY_SOCKET_IP),
                                Config.getInt(REGISTRY_SOCKET_PORT));
                    case 3 ->
                        new SocketJoinMatchManager().joinMatch(Config.getString(ENGINE_IP), Config.getInt(ENGINE_PORT),
                                Config.getString(BOOTSTRAP_IP));
                    case 4 -> {
                        ConsoleUtil.IN.close();
                        System.exit(0);
                    }
                    default -> throw new IllegalStateException("A non existing menu option was selected");
                }
            } else {
                System.out.printf("%d is not an option%n", option);
            }
        }
    }

    private static void showMenu() {
        System.out.println("1. Create profile");
        System.out.println("2. Edit profile");
        System.out.println("3. Join match");
        System.out.println("4. Exit");
        System.out.print("   Option: ");
    }

    private static void showUsage() {
        System.out.println("AA_Player <config> [token]");
    }

}
