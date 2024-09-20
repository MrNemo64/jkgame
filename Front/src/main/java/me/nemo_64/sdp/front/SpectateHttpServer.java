package me.nemo_64.sdp.front;

import com.sun.net.httpserver.HttpServer;
import me.nemo_64.sdp.utilities.configuration.Config;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SpectateHttpServer {

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(2);

    private final HttpServer server;

    public SpectateHttpServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(
                Config.getString(ConfigurationEntry.SPECTATE_HTTP_SERVER_IP),
                Config.getInt(ConfigurationEntry.SPECTATE_HTTP_SERVER_PORT)),
                0);
        server.createContext("/spectate", new SpectateHttpHandler());
        server.setExecutor(EXECUTOR_SERVICE);
        server.start();
    }

}
