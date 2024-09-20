package me.nemo_64.sdp.engine.game.element;

import com.google.gson.JsonObject;
import me.nemo_64.sdp.engine.game.Game;
import me.nemo_64.sdp.engine.game.Position;

import java.util.Optional;

public final class FoodGameElement extends GameElement.AbstractGameElement {

    public static FoodGameElement parse(JsonObject json) {
        if (!json.has("position"))
            return null;
        Optional<Position> pos = Position.fromJson(json.get("position"));
        if (pos.isEmpty())
            return null;
        return new FoodGameElement(null, pos.get());
    }

    public FoodGameElement(Game game, Position position) {
        super(game, position);
    }

    @Override
    public String mapRepresentation() {
        // return "\033[0;92m F \033[0m";
        return " F ";
    }

}
