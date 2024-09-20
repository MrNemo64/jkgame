package me.nemo_64.sdp.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import me.nemo_64.sdp.api.util.ConfigurationEntry;
import me.nemo_64.sdp.utilities.configuration.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class GameMapSupplier implements Supplier<Optional<JsonObject>> {

    private final AtomicLong lastUpdate = new AtomicLong(System.currentTimeMillis());
    private final long cacheTime = Config.getInt(ConfigurationEntry.CACHE_TIME);
    private final Path filePath = Config.getPath(ConfigurationEntry.MAP_FILE);
    private final AtomicReference<JsonObject> map = new AtomicReference<>(null);

    @Override
    public Optional<JsonObject> get() {
        if (map.get() == null || lastUpdate.get() + cacheTime < System.currentTimeMillis())
            updateCache();
        return Optional.ofNullable(map.get());
    }

    private boolean updateCache() {
        if (!Files.exists(filePath))
            return false;
        try (JsonReader reader = new JsonReader(Files.newBufferedReader(filePath))) {
            reader.setLenient(false);
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                // TODO
                return false;
            }
            JsonObject json = element.getAsJsonObject();
            /*
             * if (! json.has("map") || ! json.get("map").isJsonArray()) {
             * // TODO
             * return false;
             * }
             * if (! json.has("cities") || ! json.get("cities").isJsonArray()) {
             * // TODO
             * return false;
             * }
             */
            map.set(json);
            lastUpdate.set(System.currentTimeMillis());
            return true;
        } catch (JsonParseException e) {
            /// TODO
            return false;
        } catch (IOException e) {
            /// TODO
            return false;
        }
    }
}
