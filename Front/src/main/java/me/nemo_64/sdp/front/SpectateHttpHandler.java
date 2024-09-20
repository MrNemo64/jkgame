package me.nemo_64.sdp.front;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import me.nemo_64.sdp.utilities.HttpResponseCode;
import me.nemo_64.sdp.utilities.ResourceUtil;
import me.nemo_64.sdp.utilities.configuration.Config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SpectateHttpHandler implements HttpHandler {

    private final byte[] html;

    public SpectateHttpHandler() {
        byte[] htmlBytes = ResourceUtil.getBytes("/index.html");
        String html = new String(htmlBytes, StandardCharsets.UTF_8);
        this.html = html
                .replace("%default_map_request_url%", Config.getString(ConfigurationEntry.DEFAULT_MAP_REQUEST_URL))
                .replace("%player_request_url%", Config.getString(ConfigurationEntry.REQUEST_PLAYER_URL))
                .replace("%bot_images%", Config.getString(ConfigurationEntry.NPC_IMAGE_URL))
                .replace("%player_images%", Config.getString(ConfigurationEntry.PLAYER_IMAGE_URL))
                .replace("%food_images%", Config.getString(ConfigurationEntry.FOOD_IMAGE_URL))
                .replace("%mine_images%", Config.getString(ConfigurationEntry.MINE_IMAGE_URL))
                .getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (exchange.getRequestHeaders().containsKey("Origin"))
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin",
                    exchange.getRequestHeaders().getFirst("Origin"));
        exchange.sendResponseHeaders(HttpResponseCode.OK, html.length);
        exchange.getResponseBody().write(html);
    }

}
