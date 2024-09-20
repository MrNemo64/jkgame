package me.nemo_64.sdp.engine.game.element;

import com.google.gson.JsonObject;
import me.nemo_64.sdp.engine.game.Game;
import me.nemo_64.sdp.engine.game.Position;
import me.nemo_64.sdp.utilities.JsonUtil;
import me.nemo_64.sdp.utilities.KafkaTopic;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class NPCGameElement extends GameElement.AbstractGameElement implements GameElement.MovableGameElement {

    public static NPCGameElement parse(JsonObject json) {
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
        return new NPCGameElement(null, pos.get(), level.get(), token.get());
    }

    private final int level;
    private final String token;
    private final AtomicLong lastMovement = new AtomicLong(System.currentTimeMillis());
    private final AtomicBoolean disconnected = new AtomicBoolean(false);

    public NPCGameElement(Game game, Position position, int level, String token) {
        super(game, position);
        this.level = level;
        this.token = token;
    }

    @Override
    public void die() {
        getGame().getProducer().send(new ProducerRecord<>(KafkaTopic.NPC_JOIN_LEAVE, "die:" + token));
    }

    @Override
    public String token() {
        return token;
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
    public String display() {
        return "NPC (%s): %d".formatted(token(), getLevel());
    }

    @Override
    public InteractionResult interactWith(GameElement other) {
        if (!(other instanceof LeveledGameElement enemy))
            return InteractionResult.NOTHING;
        return fight(enemy);
    }

    @Override
    public String mapRepresentation() {
        // return "\033[0;103mNPC\033[0m";
        return "NPC";
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
    public int getRawLevel() {
        return level;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof NPCGameElement that))
            return false;
        if (!super.equals(o))
            return false;
        return Objects.equals(token, that.token);
    }

    @Override
    public String toString() {
        return "NPCGameElement{" +
                "level=" + level +
                ", token='" + token + '\'' +
                ", lastMovement=" + lastMovement +
                "} " + super.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), token);
    }
}
