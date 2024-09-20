package me.nemo_64.sdp.player.rest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import me.nemo_64.sdp.player.util.ConfigurationEntry;
import me.nemo_64.sdp.utilities.Result;
import me.nemo_64.sdp.utilities.configuration.Config;
import me.nemo_64.sdp.utilities.secure.SSLFix;

import java.io.StringReader;
import java.net.Authenticator;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class HttpClientInstance {

    private static HttpClientInstance INSTANCE;

    public static HttpClientInstance get() {
        if (INSTANCE == null)
            INSTANCE = new HttpClientInstance();
        return INSTANCE;
    }

    private final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(Config.get(ConfigurationEntry.REST_REQUEST_HTTP_VERSION, HttpClient.Version.class))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofMillis(Config.getInt(ConfigurationEntry.REST_REQUEST_TIMEOUT)))
            .sslContext(SSLFix.execute())
            // .authenticator(Authenticator.getDefault())
            .build();

    public CompletableFuture<Result<Optional<JsonElement>, String>> get(String url) {
        HttpRequest request = createRequest(url).GET().build();
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle(this::responseToJson);
    }

    public CompletableFuture<Result<Optional<JsonElement>, String>> put(String url, JsonElement body) {
        HttpRequest request = createRequest(url)
                .PUT(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle(this::responseToJson);
    }

    public CompletableFuture<Result<Optional<JsonElement>, String>> post(String url, JsonElement body) {
        HttpRequest request = createRequest(url)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        return HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle(this::responseToJson);
    }

    private Result<Optional<JsonElement>, String> responseToJson(HttpResponse<String> response, Throwable error) {
        if (error != null) {
            error.printStackTrace();
            return Result.err("Error on http: " + error.getMessage());
        }
        String responseBody = response.body();
        if (responseBody.isEmpty())
            return Result.ok(Optional.empty());
        try (JsonReader reader = new JsonReader(new StringReader(responseBody))) {
            reader.setLenient(true);
            return Result.ok(Optional.of(JsonParser.parseReader(reader)));
        } catch (Exception e) {
            return Result.err("Error on json: " + e.getMessage());
        }
    }

    private HttpRequest.Builder createRequest(String url) {
        SSLFix.execute();
        return HttpRequest.newBuilder()
                .version(Config.get(ConfigurationEntry.REST_REQUEST_HTTP_VERSION, HttpClient.Version.class))
                .uri(URI.create(url));
    }

}
