package me.nemo_64.sdp.engine.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.nemo_64.sdp.engine.Main;
import me.nemo_64.sdp.engine.util.ConfigurationEntry;
import me.nemo_64.sdp.engine.weather.WeatherRequester;
import me.nemo_64.sdp.engine.game.element.GameElement;
import me.nemo_64.sdp.engine.token.GameTokenCreationRequester;
import me.nemo_64.sdp.engine.token.TokenService;
import me.nemo_64.sdp.utilities.JsonUtil;
import me.nemo_64.sdp.utilities.ResourceUtil;
import me.nemo_64.sdp.utilities.configuration.Config;
import me.nemo_64.sdp.utilities.data.City;
import me.nemo_64.sdp.utilities.player.LoadedPlayer;
import me.nemo_64.sdp.utilities.secure.SymmetricCipher;

import javax.crypto.NoSuchPaddingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.Channels;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class GameCreator {

    private static final Logger LOGGER = Logger.getLogger(GameCreator.class.getName());

    static {
        LOGGER.setParent(Main.ENGINE_LOGGER);
    }

    public static Optional<Game> createGame(int maxPlayers, TokenService tokenService, WeatherRequester requester,
            String bootstrap) {
        String password = askForPassword();
        GamePassword requestPassword = new GamePassword(password);
        City[] cities = askForCities(requester);
        var players = waitForPlayers(maxPlayers, tokenService, requestPassword);
        if (players.isEmpty())
            return Optional.empty();
        System.out.println("Waiting for " + maxPlayers + " to join. Press <Enter> to start the match early");
        Thread t = new Thread(() -> {
            try (var scanner = new Scanner(Channels.newChannel(System.in))) {
                scanner.nextLine();
            } catch (Exception e) {
            }
            players.get().finishRequest();
        });
        t.start();
        try {
            var tokens = players.get().getFuture().get();
            t.interrupt();
            return Game.create(tokens, cities, bootstrap,
                    requestPassword.toCipher(Config.getString(ConfigurationEntry.KAFKA_ENCRYPTION_ALGORITHM)));
        } catch (InterruptedException e) {
            LOGGER.warning("Interrupted while waiting for players");
        } catch (ExecutionException e) {
            LOGGER.warning("Exception while waiting for players: " + e.getMessage());
        } catch (NoSuchPaddingException e) {
            LOGGER.warning("NoSuchPaddingException while waiting for players: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            LOGGER.warning("NoSuchAlgorithmException while waiting for players: " + e.getMessage());
        }
        return Optional.empty();
    }

    private static String askForPassword() {
        Scanner in = new Scanner(System.in);
        System.out.print("Game password (empty for random password): ");
        String pass = in.nextLine();
        if (pass.isEmpty()) {
            pass = ResourceUtil.randomString(15);
            System.out.println("Using a random generated password: " + pass);
        }
        return pass;
    }

    private static Optional<TokenService.GameTokenCreationRequest> waitForPlayers(int maxPlayers,
            TokenService tokenService, GameTokenCreationRequester.Password password) {
        Optional<TokenService.GameTokenCreationRequest> request = tokenService.requestTokensCreation(maxPlayers,
                new Requester(password));
        if (request.isEmpty()) {
            LOGGER.warning("Could not wait for players, the token service is running another request");
            return Optional.empty();
        }
        return request;
    }

    private static City[] askForCities(WeatherRequester requester) {
        Scanner in = new Scanner(System.in);
        City[] cities = { null, null, null, null };
        for (int i = 0; i < 4; i++) {
            while (true) {
                String cityName;
                while (true) {
                    System.out.printf("City #%d: ", i + 1);
                    cityName = in.nextLine();
                    if (Stream.of(cities).filter(Objects::nonNull).map(City::name).anyMatch(cityName::equals)) {
                        System.out.println("That city is already in the game.");
                    } else {
                        break;
                    }
                }

                var futureCity = requester.requestCity(cityName);
                System.out.printf("Requesting city %s to the provider%n", cityName);
                try {
                    var result = futureCity.get();
                    if (result.isSuccessful()) {
                        if (result.value().isValid()) {
                            cities[i] = result.value();
                            System.out.println("The provider sent the information: " + cities[i].prettyPrint());
                            break;
                        } else {
                            System.out.printf("The provider does not know the city %s, please try again%n", cityName);
                        }
                    } else {
                        System.out.printf("Could not request city: %s%n", cityName);
                    }
                } catch (Exception e) {
                    System.out.printf("Could not request city, please try again: %s%n", e.getMessage());
                }
            }
        }
        return cities;
    }

    public static Optional<Game> fromJson(JsonObject json, String bootstrap) {
        if (!json.has("cities") || !json.get("cities").isJsonArray()) {
            LOGGER.warning("JSON has invalid cities entry");
            return Optional.empty();
        }
        if (!json.has("map") || !json.get("map").isJsonArray()) {
            LOGGER.warning("JSON has invalid map entry");
            return Optional.empty();
        }
        Optional<City[]> cities = cities(json.get("cities").getAsJsonArray());
        if (cities.isEmpty())
            return Optional.empty();
        Optional<Collection<GameElement>> elements = elements(json.get("map").getAsJsonArray());
        if (elements.isEmpty())
            return Optional.empty();
        var secret = SymmetricCipher.deserialize(json.getAsJsonObject("password"));
        if (secret.isEmpty())
            return Optional.empty();
        return Game.create(elements.get(), cities.get(), bootstrap, secret.get());
    }

    private static Optional<Collection<GameElement>> elements(JsonArray array) {
        List<GameElement> elements = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                LOGGER.warning("JSON has invalid map entry: " + element);
                return Optional.empty();
            }
            JsonObject obj = element.getAsJsonObject();
            Optional<String> type = JsonUtil.getString(obj, "type");
            if (type.isEmpty()) {
                LOGGER.warning("JSON has invalid element type entry: " + element);
                return Optional.empty();
            }
            Optional<GameElement> parsedElement = loadElement(type.get(), obj);
            if (parsedElement.isEmpty())
                return Optional.empty();
            elements.add(parsedElement.get());
        }
        return Optional.of(elements);
    }

    private static Optional<GameElement> loadElement(String type, JsonObject data) {
        try {
            Class<?> clazz = Class.forName(type);
            Method parser = clazz.getDeclaredMethod("parse", JsonObject.class);
            Object value = parser.invoke(null, data);
            if (value == null) {
                LOGGER.warning("parse method of " + type + " returned null for " + data);
                return Optional.empty();
            }
            if (!(value instanceof GameElement element)) {
                LOGGER.warning("parse method of " + type + " does not return a game element");
                return Optional.empty();
            }
            return Optional.of(element);
        } catch (ClassNotFoundException e) {
            LOGGER.warning("Unknown game element type: " + type);
            return Optional.empty();
        } catch (NoSuchMethodException e) {
            LOGGER.warning(type + " does not have a parse method");
            return Optional.empty();
        } catch (InvocationTargetException e) {
            LOGGER.warning("parse method of " + type + " does not follow the declaration");
            return Optional.empty();
        } catch (IllegalAccessException e) {
            LOGGER.warning("Can not access parse method of " + type);
            return Optional.empty();
        }
    }

    private static Optional<City[]> cities(JsonArray array) {
        if (array.size() != 4) {
            LOGGER.warning("JSON has invalid city amount");
            return Optional.empty();
        }
        List<City> cities = new ArrayList<>(4);
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                LOGGER.warning("JSON has invalid city entry: " + element);
                return Optional.empty();
            }
            Optional<City> city = City.fromJson(element.getAsJsonObject());
            if (city.isEmpty()) {
                LOGGER.warning("JSON has invalid city entry: " + element);
                return Optional.empty();
            }
            cities.add(city.get());
        }
        return Optional.of(cities.toArray(City[]::new));
    }

    private static final class Requester implements GameTokenCreationRequester {

        private final Password password;

        private Requester(Password password) {
            this.password = password;
        }

        @Override
        public void onTokenCreated(String token, LoadedPlayer assignee) {
            LOGGER.info("Assigned %s (%s) the token %s".formatted(assignee.getAlias(), assignee.getId(), token));
            System.out.printf("%s(%s) joined the game %n", assignee.getAlias(), assignee.getId().toString());
        }

        @Override
        public void onRequestCompleted(Map<String, LoadedPlayer> tokens) {
            LOGGER.info(tokens.size() + " tokens created");
        }

        @Override
        public Password password() {
            return password;
        }
    }

}
