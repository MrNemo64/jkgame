package me.nemo_64.sdp.engine;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import me.nemo_64.sdp.engine.game.Game;
import me.nemo_64.sdp.engine.game.GameCreator;
import me.nemo_64.sdp.engine.game.tasks.SaveGameStateTask;
import me.nemo_64.sdp.engine.token.TokenService;
import me.nemo_64.sdp.engine.weather.WeatherRequester;
import me.nemo_64.sdp.utilities.NetworkUtil;
import me.nemo_64.sdp.utilities.NumberUtil;
import me.nemo_64.sdp.utilities.configuration.Config;
import me.nemo_64.sdp.utilities.configuration.ConfigurationBuilder;
import me.nemo_64.sdp.utilities.player.PlayerManager;
import me.nemo_64.sdp.utilities.player.PlayerUtil;
import me.nemo_64.sdp.utilities.secure.SecurityConfigFilters;

import java.io.IOException;
import java.io.Reader;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static me.nemo_64.sdp.engine.util.ConfigurationEntry.*;

public class Main {

    public static PlayerManager PLAYER_MANAGER;

    public static final Logger ENGINE_LOGGER = Logger.getLogger("AA_Engine");

    static {
        ENGINE_LOGGER.setUseParentHandlers(false);
        try {
            Path file = Paths.get("log/AA_Engine." + (System.currentTimeMillis() / 1000) + ".log");
            Path parent = file.getParent();
            if (!Files.exists(parent))
                Files.createDirectories(parent);
            Files.createFile(file);
            FileHandler handler = new FileHandler(file.toString());
            handler.setFormatter(new SimpleFormatter());
            ENGINE_LOGGER.addHandler(handler);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        PlayerManager.setLoggerParent(ENGINE_LOGGER);
        PlayerUtil.setLoggerParent(ENGINE_LOGGER);
        Logger kafkaLogger = Logger.getLogger("kafka");
        kafkaLogger.setParent(ENGINE_LOGGER);
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
                .register(PORT, Integer.class, false, 6002, NetworkUtil::isValidPort)
                .register(MAX_PLAYERS, Integer.class, false, 3, NumberUtil::isGraterThanZero)
                .register(BOOTSTRAP_IP, String.class, false, "localhost:9092")
                .register(ATTEMPTS_PER_REQUEST, Integer.class, false, 3, NumberUtil::isGraterThanZero)
                .register(TIME_OUT, Integer.class, false, 1000, NumberUtil::isGraterThanZero)
                .register(PLAYERS_FOLDER, String.class, false, "players")
                .register(ALLOWED_CHARACTERS_FILE, String.class, true)
                .register(WEATHER_REQUEST_HTTP_VERSION, HttpClient.Version.class, false, HttpClient.Version.HTTP_1_1)
                .register(WEATHER_REQUEST_TIMEOUT, Integer.class, false, 2000, NumberUtil::isGraterThanZero)
                .register(WEATHER_REQUEST_URI, String.class, false, WeatherRequester.DEFAULT_URI)
                .register(WEATHER_REQUEST_TOKEN, String.class, false, WeatherRequester.DEFAULT_TOKEN)
                .register(SAVE_MAP_PERIOD, Integer.class, true, 17, NumberUtil::isGraterThanZero)
                .register(KAFKA_ENCRYPTION_ALGORITHM, String.class, false, "AES",
                        SecurityConfigFilters::isValidCipherAlgorithm)
                .withLogger(ENGINE_LOGGER);
        if (args.length == 1) {
            confBuilder.withFile(Path.of(args[0]));
        }
        var conf = confBuilder.create();
        if (conf.isError()) {
            System.out.println("Could not load config: " + conf.error().message());
            System.exit(-1);
        }
        Config.setInstance(conf.value());

        ENGINE_LOGGER.info("Using the following configuration:" + Config.getInstance().display("  "));

        var playerManager = PlayerManager.setUp(Config.getPath(PLAYERS_FOLDER),
                Config.getOptionalPath(ALLOWED_CHARACTERS_FILE).orElse(null));
        if (playerManager.isError()) {
            System.out.println("Error while preparing players: " + playerManager.error().name());
            System.exit(-1);
        }
        PLAYER_MANAGER = playerManager.value();

        var tokenServiceResult = TokenService.createService(Config.getInt(PORT), Config.getInt(ATTEMPTS_PER_REQUEST),
                Config.getInt(TIME_OUT), playerManager.value());
        if (tokenServiceResult.isError()) {
            System.out.println("Could not create the token service. Engine will close");
            System.exit(-1);
        }
        TokenService tokens = tokenServiceResult.value();
        if (!tokens.start()) {
            System.out.println("Could not start the token service. Engine will close");
            System.exit(-1);
        }

        WeatherRequester weather = WeatherRequester.create();

        Optional<Game> game = recoverGame(Config.getString(BOOTSTRAP_IP));
        if (game.isPresent()) {
            System.out.println("Game recovered");
            System.out.println("Continuing game");
            game.get().startGame();
        } else {
            // NEW MATCH
            System.out.println("Creating new match");
            game = GameCreator.createGame(Config.getInt(MAX_PLAYERS), tokens, weather, Config.getString(BOOTSTRAP_IP));
            if (game.isEmpty()) {
                System.out.println("Could not create game");
                System.exit(-1);
            }
            System.out.println("Starting game");
            game.get().startGame();
        }
    }

    private static Optional<Game> recoverGame(String boostrap) {
        Path file = SaveGameStateTask.GAME_STATE_FILE;
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        System.out.println("Recovering last game");
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setLenient(false);
            JsonElement element = JsonParser.parseReader(jsonReader);
            if (!element.isJsonObject()) {
                System.out.println("Invalid JSON file");
                ENGINE_LOGGER.warning("Invalid JSON file");
                return Optional.empty();
            }
            return GameCreator.fromJson(element.getAsJsonObject(), boostrap);
        } catch (JsonParseException e) {
            System.out.println("JSON exception while recovering game file: " + e.getMessage());
            ENGINE_LOGGER.warning("JSON exception while recovering game file: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("IO exception while recovering game file: " + e.getMessage());
            ENGINE_LOGGER.warning("IO exception while recovering game file: " + e.getMessage());
        }
        return Optional.empty();
    }

    private static void showUsage() {
        // System.out.println("AA_Engine <port> <max players> <weather ip> <weather
        // port> <bootstrap ip> <bootstrap port> <attempts per request> <time out>
        // <players folder> [allowed characters file]");
        System.out.println("AA_Engine [config]");
    }

}
