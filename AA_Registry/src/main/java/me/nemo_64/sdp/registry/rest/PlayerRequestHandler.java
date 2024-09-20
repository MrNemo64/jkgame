package me.nemo_64.sdp.registry.rest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import me.nemo_64.sdp.registry.util.ConfigurationEntry;
import me.nemo_64.sdp.utilities.HttpResponseCode;
import me.nemo_64.sdp.utilities.configuration.Config;
import me.nemo_64.sdp.utilities.player.LoadedPlayer;
import me.nemo_64.sdp.utilities.player.PlayerManager;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

public class PlayerRequestHandler implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(PlayerRequestHandler.class.getName());

    static {
        LOGGER.setParent(PlayerHttpsServer.LOGGER);
        LOGGER.setUseParentHandlers(true);
    }

    private final PlayerManager playerManager;

    PlayerRequestHandler(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    @Override
    public void handle(HttpExchange exchange) {
        try (exchange) {
            if (exchange.getRequestHeaders().containsKey("Origin"))
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin",
                        exchange.getRequestHeaders().getFirst("Origin"));
            String requesterAddress = exchange.getRemoteAddress().getHostName() + ":"
                    + exchange.getRemoteAddress().getPort();
            String[] args = exchange.getRequestURI().toString().split("/");
            if (args[0].isEmpty())
                args = Arrays.copyOfRange(args, 1, args.length);
            switch (exchange.getRequestMethod()) {
                case "GET" -> handleGet(exchange, args, requesterAddress);
                case "PUT" -> handlePut(exchange, args, requesterAddress);
                case "POST" -> handlePost(exchange, args, requesterAddress);
                default -> handleUnsupported(exchange, args, requesterAddress);
            }
        }
    }

    private void handleUnsupported(HttpExchange exchange, String[] args, String requesterAddress) {
        try {
            LOGGER.info(
                    requesterAddress + " tried to " + exchange.getRequestMethod() + " but that method is not allowed");
            exchange.sendResponseHeaders(HttpResponseCode.METHOD_NOT_ALLOWED, 0);
        } catch (IOException e) {
            LOGGER.info("IO exception while declining %s: %s".formatted(requesterAddress, e.getMessage()));
        }
    }

    private boolean checkPostBody(JsonObject body, HttpExchange exchange, LoadedPlayer player,
            String requesterAddress) {
        try {
            if (!(body.has("target") && body.get("target").isJsonPrimitive()
                    && body.getAsJsonPrimitive("target").isString())) {
                byte[] message = Config.getString(ConfigurationEntry.POST_MISSING_FILED_RESPONSE)
                        .replace("%field%", "target").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(HttpResponseCode.UNPROCESSABLE_ENTITY, message.length);
                exchange.getResponseBody().write(message);
                LOGGER.info("POST: " + requesterAddress + " sent an invalid body for updating " + player.getAlias());
                return false;
            }
            if (!(body.has("password") && body.get("password").isJsonPrimitive()
                    && body.getAsJsonPrimitive("password").isString())) {
                byte[] message = Config.getString(ConfigurationEntry.POST_MISSING_FILED_RESPONSE)
                        .replace("%field%", "password").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(HttpResponseCode.UNPROCESSABLE_ENTITY, message.length);
                exchange.getResponseBody().write(message);
                LOGGER.info("POST: " + requesterAddress + " sent an invalid body for updating " + player.getAlias());
                return false;
            }
            if (!(body.has("values") && body.get("values").isJsonObject())) {
                byte[] message = Config.getString(ConfigurationEntry.POST_MISSING_FILED_RESPONSE)
                        .replace("%field%", "values").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(HttpResponseCode.UNPROCESSABLE_ENTITY, message.length);
                exchange.getResponseBody().write(message);
                LOGGER.info("POST: " + requesterAddress + " sent an invalid body for updating " + player.getAlias());
                return false;
            }
            if (!body.getAsJsonPrimitive("password").getAsString().equals(player.getPassword())) {
                byte[] message = Config.getString(ConfigurationEntry.INCORRECT_PASSWORD_POST_RESPONSE)
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(HttpResponseCode.FORBIDDEN_ACCESS, message.length);
                exchange.getResponseBody().write(message);
                LOGGER.info("POST: " + requesterAddress + " sent the incorrect password for " + player.getAlias());
                return false;
            }
            return true;
        } catch (IOException e) {
            LOGGER.warning("POST: IO exception while serving %s: %s".formatted(requesterAddress, e.getMessage()));
            return false;
        }
    }

    private void handlePost(HttpExchange exchange, String[] args, String requesterAddress) {
        try {
            var oBody = getBody(exchange);
            if (oBody.isEmpty()) {
                byte[] response = Config.getString(ConfigurationEntry.REQUEST_BODY_INVALID)
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(HttpResponseCode.UNPROCESSABLE_ENTITY, response.length);
                exchange.getResponseBody().write(response);
                return;
            }
            var body = oBody.get();
            String playerAlias = (body.has("target") && body.get("target").isJsonPrimitive()
                    && body.getAsJsonPrimitive("target").isString()) ? body.getAsJsonPrimitive("target").getAsString()
                            : "";

            var oPlayer = playerManager.getPlayer(playerAlias);
            if (oPlayer.isEmpty()) {
                LOGGER.info("POST: " + requesterAddress + " tried to update " + playerAlias
                        + " but there is no player with the specified alias.");
                byte[] response = Config.getString(ConfigurationEntry.PLAYER_NOT_FOUND_RESPONSE)
                        .replace("%player_alias%", playerAlias)
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(HttpResponseCode.NOT_FOUND, response.length);
                exchange.getResponseBody().write(response);
                return;
            }
            var player = oPlayer.get();
            if (!checkPostBody(oBody.get(), exchange, player, requesterAddress))
                return;

            var newPlayerData = extractValues(oBody.get().getAsJsonObject("values"));
            if (!newPlayerData.atLeastOneFieldPresent()) {
                LOGGER.info(
                        "POST: " + requesterAddress + " tried to update a player but no new fields were specified.");
                byte[] response = Config.getString(ConfigurationEntry.REQUEST_BODY_INVALID)
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(HttpResponseCode.UNPROCESSABLE_ENTITY, response.length);
                exchange.getResponseBody().write(response);
                return;
            }

            newPlayerData.alias().ifPresent(player::setAlias);
            newPlayerData.password().ifPresent(player::setPassword);
            newPlayerData.hotEffect().ifPresent(player::setHotEffect);
            newPlayerData.coldEffect().ifPresent(player::setColdEffect);
            player.save();
            LOGGER.info("POST: %s updated %s(%s)".formatted(requesterAddress, player.getAlias(), player.getId()));
            exchange.sendResponseHeaders(HttpResponseCode.OK, 0);
        } catch (IOException e) {
            LOGGER.warning("POST: IO exception while serving %s: %s".formatted(requesterAddress, e.getMessage()));
        }
    }

    private void handlePut(HttpExchange exchange, String[] args, String requesterAddress) {
        try {
            var oBody = getBody(exchange);
            if (oBody.isEmpty()) {
                byte[] response = Config.getString(ConfigurationEntry.REQUEST_BODY_INVALID)
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(HttpResponseCode.UNPROCESSABLE_ENTITY, response.length);
                exchange.getResponseBody().write(response);
                return;
            }

            var newPlayerData = extractValues(oBody.get());
            if (!newPlayerData.allFieldsPresent()) {
                LOGGER.info(
                        "PUT: " + requesterAddress + " tried to create a player but not all fields were specified.");
                byte[] response = Config.getString(ConfigurationEntry.REQUEST_BODY_INVALID)
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(HttpResponseCode.UNPROCESSABLE_ENTITY, response.length);
                exchange.getResponseBody().write(response);
                return;
            }

            var oPlayer = playerManager.getPlayer(newPlayerData.alias().get());
            if (oPlayer.isPresent() || !playerManager.isValidAlias(newPlayerData.alias().get())) {
                LOGGER.info("PUT: " + requesterAddress + " tried to create " + newPlayerData.alias().get()
                        + " but there is already a player with the specified alias or the alias is invalid.");
                byte[] response = Config.getString(ConfigurationEntry.ALIAS_NOT_AVAILABLE_RESPONSE)
                        .replace("%player_alias%", newPlayerData.alias().get())
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(HttpResponseCode.NOT_FOUND, response.length);
                exchange.getResponseBody().write(response);
                return;
            }

            var result = playerManager.createPlayer(newPlayerData.alias().get(), newPlayerData.password().get(),
                    newPlayerData.hotEffect().get(), newPlayerData.coldEffect().get());
            if (result.isError()) {
                LOGGER.warning(
                        "PUT: Could not create player while serving " + requesterAddress + ": " + result.error());
                byte[] response = Config.getString(ConfigurationEntry.PLAYER_CREATE_ERROR_RESPONSE)
                        .replace("%error%", result.error().name())
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(HttpResponseCode.UNPROCESSABLE_ENTITY, response.length);
                exchange.getResponseBody().write(response);
            } else {
                LOGGER.info("PUT: " + requesterAddress + " created a new player: " + result.value());
                exchange.sendResponseHeaders(HttpResponseCode.OK, 0);
            }
        } catch (IOException e) {
            LOGGER.warning("PUT: IO exception while serving %s: %s".formatted(requesterAddress, e.getMessage()));
        }
    }

    private void handleGet(HttpExchange exchange, String[] args, String requesterAddress) {
        try {
            if (args.length != 2) {
                LOGGER.info("GET: " + requesterAddress + " tried to request a player but no alias was specified.");
                byte[] response = Config.getString(ConfigurationEntry.PLAYER_NOT_SPECIFIED_RESPONSE)
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(HttpResponseCode.METHOD_NOT_ALLOWED, response.length);
                exchange.getResponseBody().write(response);
                return;
            }
            String playerAlias = args[1];
            var player = playerManager.getPlayer(playerAlias);
            if (player.isEmpty()) {
                LOGGER.info("GET: " + requesterAddress + " requested " + playerAlias
                        + " but there is no player with the specified alias.");
                byte[] response = Config.getString(ConfigurationEntry.PLAYER_NOT_FOUND_RESPONSE)
                        .replace("%player_alias%", playerAlias)
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(HttpResponseCode.NOT_FOUND, response.length);
                exchange.getResponseBody().write(response);
                return;
            }
            LOGGER.info("GET: " + requesterAddress + " requested " + playerAlias + ". Successfully sent the response.");
            var json = player.map(LoadedPlayer::serialize).map(Objects::toString)
                    .map((str) -> str.getBytes(StandardCharsets.UTF_8)).get();
            exchange.sendResponseHeaders(HttpResponseCode.OK, json.length);
            exchange.getResponseBody().write(json);
        } catch (IOException e) {
            LOGGER.warning("GET: IO exception while serving %s: %s".formatted(requesterAddress, e.getMessage()));
        }
    }

    private PlayerValues extractValues(JsonObject json) {
        var alias = getStringFiled(json, "alias");
        var password = getStringFiled(json, "password");
        var hotEffect = getIntFiled(json, "hot-effect");
        var coldEffect = getIntFiled(json, "cold-effect");
        return new PlayerValues(alias, password, hotEffect, coldEffect);
    }

    private Optional<JsonObject> getBody(HttpExchange exchange) {
        try (JsonReader reader = new JsonReader(new InputStreamReader(exchange.getRequestBody()))) {
            reader.setLenient(false);
            JsonElement element = JsonParser.parseReader(reader);
            return Optional.of(element)
                    .filter(JsonElement::isJsonObject)
                    .map(JsonElement::getAsJsonObject);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Optional<String> getStringFiled(JsonObject json, String name) {
        return Optional.ofNullable(json.has(name)
                && json.get(name).isJsonPrimitive()
                && json.getAsJsonPrimitive(name).isString() ? json.getAsJsonPrimitive(name).getAsString() : null);
    }

    private static Optional<Integer> getIntFiled(JsonObject json, String name) {
        return Optional.ofNullable(json.has(name)
                && json.get(name).isJsonPrimitive()
                && json.getAsJsonPrimitive(name).isNumber() ? json.getAsJsonPrimitive(name).getAsInt() : null);
    }

    private record PlayerValues(Optional<String> alias, Optional<String> password, Optional<Integer> hotEffect,
            Optional<Integer> coldEffect) {

        public boolean allFieldsPresent() {
            return alias().isPresent() && password().isPresent() && hotEffect().isPresent() && coldEffect.isPresent();
        }

        public boolean atLeastOneFieldPresent() {
            return alias().isPresent() || password().isPresent() || hotEffect().isPresent() || coldEffect.isPresent();
        }

    }

}
