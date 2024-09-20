package me.nemo_64.sdp.engine.game.element;

import com.google.gson.JsonObject;
import me.nemo_64.sdp.engine.game.Game;
import me.nemo_64.sdp.engine.game.Position;

import java.util.Objects;

public interface GameElement {

    enum InteractionResult {
        NOTHING,
        REMOVE_OTHER,
        REMOVE_SELF,
        REMOVE_BOTH;
    }

    default JsonObject asJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", getClass().getName());
        json.add("position", getPosition().asJson());
        return json;
    }

    void assignGame(Game game);

    default boolean shouldBeSaved() {
        return true;
    }

    default void notifyRemoved() {
    }

    void die();

    boolean isStillAlive();

    String mapRepresentation();

    Game getGame();

    Position getPosition();

    void setPosition(Position pos);

    interface LeveledGameElement extends GameElement {

        default int getLevel() {
            return getRawLevel();
        }

        int getRawLevel();

        default MovableGameElement.InteractionResult fight(LeveledGameElement other) {
            int enemyLevel = other.getLevel();
            int selfLevel = getLevel();
            if (enemyLevel > selfLevel)
                return MovableGameElement.InteractionResult.REMOVE_SELF;
            if (enemyLevel < selfLevel)
                return MovableGameElement.InteractionResult.REMOVE_OTHER;
            return MovableGameElement.InteractionResult.NOTHING;
        }

        @Override
        default JsonObject asJson() {
            JsonObject json = GameElement.super.asJson();
            json.addProperty("level", getRawLevel());
            return json;
        }
    }

    interface MovableGameElement extends LeveledGameElement {
        String token();

        void setLastMovementTimeStamp(long moment);

        long lastMovementTimeStamp();

        InteractionResult interactWith(GameElement other);

        @Override
        default JsonObject asJson() {
            JsonObject json = LeveledGameElement.super.asJson();
            json.addProperty("token", token());
            return json;
        }

        String display();

        boolean isDisconnected();

        void setDisconnected(boolean disconnected);

    }

    abstract class AbstractGameElement implements GameElement {

        private Game game;
        private Position position;
        private boolean alive = true;

        public AbstractGameElement(Game game, Position position) {
            this.game = game;
            this.position = position;
        }

        @Override
        public void assignGame(Game game) {
            this.game = game;
        }

        @Override
        public Game getGame() {
            return game;
        }

        @Override
        public void setPosition(Position pos) {
            this.position = pos;
        }

        @Override
        public void die() {
            alive = false;
        }

        @Override
        public Position getPosition() {
            return position;
        }

        @Override
        public void notifyRemoved() {
            alive = false;
        }

        @Override
        public boolean isStillAlive() {
            return alive;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof AbstractGameElement that))
                return false;
            return Objects.equals(game, that.game) && Objects.equals(position, that.position);
        }

        @Override
        public int hashCode() {
            return Objects.hash(game, position);
        }
    }

}
