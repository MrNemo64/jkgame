package me.nemo_64.sdp.api.rest;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import me.nemo_64.sdp.api.util.ConfigurationEntry;
import me.nemo_64.sdp.utilities.HttpResponseCode;
import me.nemo_64.sdp.utilities.NetworkUtil;
import me.nemo_64.sdp.utilities.configuration.Config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class GameHttpServerHandler implements HttpHandler {

    private static final Logger LOGGER = Logger.getLogger(GameHttpServerHandler.class.getName());

    private final Supplier<Optional<JsonObject>> responseSupplier;

    GameHttpServerHandler(Supplier<Optional<JsonObject>> responseSupplier) {
        this.responseSupplier = responseSupplier;
    }

    @Override
    public void handle(HttpExchange exchange) {
        try (exchange) {
            if (exchange.getRequestHeaders().containsKey("Origin"))
                exchange.getResponseHeaders().add("Access-Control-Allow-Origin",
                        exchange.getRequestHeaders().getFirst("Origin"));
            String[] args = exchange.getRequestURI().toString().split("/");
            switch (exchange.getRequestMethod()) {
                case "GET" -> handleGet(exchange, args);
                default -> handleUnsupported(exchange, args);
            }
        } catch (IOException e) {
            LOGGER.warning("IO exception while serving " + NetworkUtil.addressOf(exchange.getRemoteAddress()) + ": "
                    + e.getMessage());
        }
    }

    private void handleUnsupported(HttpExchange exchange, String[] args) throws IOException {
        byte[] message = Config.getString(ConfigurationEntry.UNSUPPORTED_METHOD_MESSAGE)
                .getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(HttpResponseCode.METHOD_NOT_ALLOWED, message.length);
        exchange.getResponseBody().write(message);
        LOGGER.info(NetworkUtil.addressOf(exchange.getRemoteAddress()) + " tried to " + exchange.getRequestMethod()
                + " but its not supported");
    }

    private void handleGet(HttpExchange exchange, String[] args) throws IOException {
        String address = NetworkUtil.addressOf(exchange.getRemoteAddress());
        var game = responseSupplier.get().map(JsonObject::toString).map((str) -> str.getBytes(StandardCharsets.UTF_8));
        if (game.isEmpty()) {
            byte[] response = Config.getString(ConfigurationEntry.NO_GAME_FOUND_MESSAGE)
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(HttpResponseCode.NOT_FOUND, response.length);
            exchange.getResponseBody().write(response);
            LOGGER.info(address + " requested the game info but here are no ongoing games");
            return;
        }
        byte[] gameInfo = game.get();
        exchange.sendResponseHeaders(HttpResponseCode.OK, gameInfo.length);
        exchange.getResponseBody().write(gameInfo);
        LOGGER.info("Sent " + address + " the game info");
    }
}
