package me.nemo_64.sdp.engine.game.element;

import com.google.gson.JsonObject;
import me.nemo_64.sdp.engine.Main;
import me.nemo_64.sdp.engine.game.Game;
import me.nemo_64.sdp.engine.game.Position;
import me.nemo_64.sdp.utilities.JsonUtil;
import me.nemo_64.sdp.utilities.KafkaTopic;
import me.nemo_64.sdp.utilities.player.LoadedPlayer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class PlayerGameElement extends GameElement.AbstractGameElement implements GameElement.MovableGameElement {

    public static PlayerGameElement parse(JsonObject json) {
        if (!json.has("position"))
            return null;
        Optional<Position> pos = Position.fromJson(json.get("position"));
        if (pos.isEmpty())
            return null;
        Optional<Integer> level = JsonUtil.getNumber(json, "level").map(Number::intValue);
        if (level.isEmpty() || level.get() <= 0)
            return null;
        Optional<String> token = JsonUtil.getString(json, "token");
        if (token.isEmpty())
            return null;
        Optional<UUID> id = JsonUtil.getUUID(json, "player");
        if (id.isEmpty())
            return null;
        Optional<LoadedPlayer> player = Main.PLAYER_MANAGER.getPlayer(id.get());
        if (player.isEmpty())
            return null;
        return new PlayerGameElement(null, pos.get(), player.get(), level.get(), token.get());
    }

    private final LoadedPlayer player;
    private int level;
    private final String token;
    private final AtomicLong lastMovement = new AtomicLong(System.currentTimeMillis());
    private final AtomicBoolean disconnected = new AtomicBoolean(false);

    public PlayerGameElement(Game game, Position position, LoadedPlayer player, int level, String token) {
        super(game, position);
        this.player = player;
        this.token = token;
        this.level = level;
    }

    @Override
    public JsonObject asJson() {
        JsonObject json = MovableGameElement.super.asJson();
        json.addProperty("player", player.getId().toString());
        json.addProperty("player-name", player.getAlias());
        return json;
    }

    @Override
    public String display() {
        return "%s (%s): %d".formatted(player.getAlias(), token(), getLevel());
    }

    @Override
    public void die() {
        super.die();
        getGame().getProducer().send(new ProducerRecord<>(KafkaTopic.PLAYER_GAME_UPDATES, "death:" + token()));
    }

    @Override
    public void notifyRemoved() {

    }

    public int getRawLevel() {
        return level;
    }

    public int getLevel() {
        return Math.max(getRawLevel() + getTemperatureModifier(), 1);
    }

    public void levelUp() {
        level++;
    }

    public int getTemperatureModifier() {
        if (getPosition().x() < 0 || getPosition().x() >= 20)
            return 0;
        if (getPosition().y() < 0 || getPosition().y() >= 20)
            return 0;
        double temperature = getGame().getCity(getPosition().x(), getPosition().y()).temperature();
        if (temperature <= 10)
            return player.getColdEffect();
        if (temperature >= 25)
            return player.getHotEffect();
        return 0;
    }

    public LoadedPlayer getPlayer() {
        return player;
    }

    @Override
    public String token() {
        return token;
    }

    @Override
    public InteractionResult interactWith(GameElement other) {
        if (other instanceof MineGameElement) {
            return InteractionResult.REMOVE_BOTH;
        } else if (other instanceof FoodGameElement) {
            levelUp();
            return InteractionResult.REMOVE_OTHER;
        } else if (other instanceof LeveledGameElement enemy) {
            return fight(enemy);
        }
        return InteractionResult.NOTHING;
    }

    @Override
    public String mapRepresentation() {
        String alias = player.getAlias();
        if (alias.length() == 1) {
            alias = " " + alias + " ";
        } else if (alias.length() == 2) {
            alias += " ";
        } else if (alias.length() > 3) {
            alias = alias.substring(0, 3);
        }
        // return "\033[0;105m" + alias + "\033[0m";
        return alias;
    }

    @Override
    public void setLastMovementTimeStamp(long moment) {
        lastMovement.set(moment);
    }

    @Override
    public long lastMovementTimeStamp() {
        return lastMovement.get();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof PlayerGameElement that))
            return false;
        if (!super.equals(o))
            return false;
        return Objects.equals(token, that.token);
    }

    @Override
    public boolean isDisconnected() {
        return disconnected.get();
    }

    @Override
    public void setDisconnected(boolean disconnected) {
        this.disconnected.set(disconnected);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), token);
    }
}
