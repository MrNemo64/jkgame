package me.nemo_64.sdp.utilities;

import com.google.gson.JsonObject;

import java.util.Optional;
import java.util.UUID;

public class JsonUtil {

    public static Optional<String> getString(JsonObject json, String key) {
        if (!json.has(key))
            return Optional.empty();
        if (!json.get(key).isJsonPrimitive() || !json.get(key).getAsJsonPrimitive().isString())
            return Optional.empty();
        return Optional.of(json.get(key).getAsJsonPrimitive().getAsString());
    }

    public static Optional<Number> getNumber(JsonObject json, String key) {
        if (!json.has(key))
            return Optional.empty();
        if (!json.get(key).isJsonPrimitive() || !json.get(key).getAsJsonPrimitive().isNumber())
            return Optional.empty();
        return Optional.of(json.get(key).getAsJsonPrimitive().getAsNumber());
    }

    public static Optional<UUID> getUUID(JsonObject json, String key) {
        Optional<String> str = JsonUtil.getString(json, key);
        if (str.isEmpty())
            return Optional.empty();
        try {
            return Optional.of(UUID.fromString(str.get()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

}
