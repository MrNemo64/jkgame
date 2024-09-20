package me.nemo_64.sdp.engine.weather;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import me.nemo_64.sdp.engine.Main;
import me.nemo_64.sdp.engine.util.ConfigurationEntry;
import me.nemo_64.sdp.utilities.Result;
import me.nemo_64.sdp.utilities.configuration.Config;
import me.nemo_64.sdp.utilities.data.City;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class WeatherRequester {

    public static final String DEFAULT_URI = "https://api.openweathermap.org/data/2.5/weather?q={city_name}&appid={API_key}&units=metric";
    public static final String DEFAULT_TOKEN = "";
    private static final ExecutorService REQUESTERS = Executors.newSingleThreadExecutor();
    private static final Logger LOGGER = Logger.getLogger(WeatherRequester.class.getName());

    static {
        LOGGER.setParent(Main.ENGINE_LOGGER);
    }

    public static WeatherRequester create() {
        return new WeatherRequester();
    }

    private final HttpClient client;

    private WeatherRequester() {
        client = HttpClient.newBuilder()
                .version(Config.get(ConfigurationEntry.WEATHER_REQUEST_HTTP_VERSION, HttpClient.Version.class))
                .connectTimeout(Duration.ofMillis(Config.getInt(ConfigurationEntry.WEATHER_REQUEST_TIMEOUT)))
                .build();
    }

    public CompletableFuture<Result<City, RequestCityError>> requestCity(String city) {
        return client.sendAsync(createRequest(city), HttpResponse.BodyHandlers.ofString())
                .handle((response, error) -> {
                    if (error != null) {
                        return processError(error);
                    } else {
                        return parseResponse(response, city);
                    }
                });
    }

    private Result<City, RequestCityError> processError(Throwable error) {
        return Result.err(RequestCityError.ofThrowable(error));
    }

    private Result<City, RequestCityError> parseResponse(HttpResponse<String> response, String city) {
        String body = response.body();
        try (JsonReader reader = new JsonReader(new StringReader(body))) {
            reader.setLenient(false);
            JsonElement element = JsonParser.parseReader(reader);
            double temp = element.getAsJsonObject()
                    .getAsJsonObject("main")
                    .getAsJsonPrimitive("temp")
                    .getAsNumber().doubleValue();
            return Result.ok(new City(city, temp));
        } catch (IOException e) {
            return Result.err(RequestCityError.ofThrowable(e));
        }
    }

    private HttpRequest createRequest(String city) {
        String uri = Config.getString(ConfigurationEntry.WEATHER_REQUEST_URI);
        uri = uri.replace("{API_key}", Config.getString(ConfigurationEntry.WEATHER_REQUEST_TOKEN));
        uri = uri.replace("{city_name}", city);
        return HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(uri))
                .build();
    }

}
