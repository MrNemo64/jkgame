package me.nemo_64.sdp.engine.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.Optional;

public record Position(int x, int y) {

    public static Optional<Position> fromJson(JsonElement element) {
        if (!element.isJsonArray())
            return Optional.empty();
        JsonArray array = element.getAsJsonArray();
        if (array.size() != 2)
            return Optional.empty();
        int[] pos = { 0, 0 };
        for (int i = 0; i < 2; i++) {
            JsonElement el = array.get(i);
            if (!el.isJsonPrimitive() || !el.getAsJsonPrimitive().isNumber())
                return Optional.empty();
            pos[i] = el.getAsJsonPrimitive().getAsNumber().intValue();
        }
        return Optional.of(new Position(pos[0], pos[1]));
    }

    public Position plus(int x, int y) {
        return new Position(x() + x, y() + y);
    }

    public Position normalized() {
        int x = x();
        int y = y();
        if (x < 0)
            x = 19;
        if (y < 0)
            y = 19;
        if (x > 19)
            x = 0;
        if (y > 19)
            y = 0;
        return new Position(x, y);
    }

    @Override
    public int hashCode() {
        return (x << 16) ^ y;
    }

    public JsonArray asJson() {
        JsonArray array = new JsonArray(2);
        array.add(x());
        array.add(y());
        return array;
    }
}
