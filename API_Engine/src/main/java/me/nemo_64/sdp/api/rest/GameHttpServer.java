package me.nemo_64.sdp.api.rest;

import com.sun.net.httpserver.HttpServer;
import me.nemo_64.sdp.api.GameMapSupplier;
import me.nemo_64.sdp.api.util.ConfigurationEntry;
import me.nemo_64.sdp.utilities.configuration.Config;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameHttpServer {

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(2);

    private final HttpServer server;

    public GameHttpServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(
                Config.getString(ConfigurationEntry.GAME_HTTP_SERVER_IP),
                Config.getInt(ConfigurationEntry.GAME_HTTP_SERVER_PORT)),
                0);
        server.createContext("/game", new GameHttpServerHandler(new GameMapSupplier()));
        server.setExecutor(EXECUTOR_SERVICE);
        server.start();
    }

}
