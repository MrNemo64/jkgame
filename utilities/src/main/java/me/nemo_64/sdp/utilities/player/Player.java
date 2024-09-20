package me.nemo_64.sdp.utilities.player;

import com.google.gson.JsonObject;
import me.nemo_64.sdp.utilities.JsonUtil;
import me.nemo_64.sdp.utilities.Result;

import java.util.*;

public class Player {

    public enum PlayerDeserializationError {
        INVALID_ID,
        INVALID_ALIAS,
        INVALID_PASSWORD,
        INVALID_HOT_EFFECT,
        INVALID_COLD_EFFECT,
    }

    private final UUID id;
    private String alias;
    private String password;
    private int hotEffect;
    private int coldEffect;

    public static Result<Player, PlayerDeserializationError> deserialize(JsonObject json) {
        var id = JsonUtil.getUUID(json, "id");
        if (id.isEmpty())
            return Result.err(PlayerDeserializationError.INVALID_ID);
        var alias = JsonUtil.getString(json, "alias");
        if (alias.isEmpty())
            return Result.err(PlayerDeserializationError.INVALID_ALIAS);
        var password = JsonUtil.getString(json, "password");
        if (password.isEmpty())
            return Result.err(PlayerDeserializationError.INVALID_PASSWORD);
        var hotEffect = JsonUtil.getNumber(json, "hot-effect");
        if (hotEffect.isEmpty())
            return Result.err(PlayerDeserializationError.INVALID_HOT_EFFECT);
        var coldEffect = JsonUtil.getNumber(json, "cold-effect");
        if (coldEffect.isEmpty())
            return Result.err(PlayerDeserializationError.INVALID_COLD_EFFECT);
        return Result.ok(new Player(id.get(), alias.get(), password.get(), hotEffect.get().intValue(),
                coldEffect.get().intValue()));
    }

    public Player(UUID id, String alias, String password, int hotEffect, int coldEffect) {
        this.id = id;
        this.alias = alias;
        this.password = password;
        this.hotEffect = hotEffect;
        this.coldEffect = coldEffect;
    }

    @Override
    public String toString() {
        return "Player{" +
                "id=" + id +
                ", alias='" + alias + '\'' +
                ", password='" + password + '\'' +
                ", hotEffect=" + hotEffect +
                ", coldEffect=" + coldEffect +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Player player))
            return false;
        return id.equals(player.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public UUID getId() {
        return id;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getHotEffect() {
        return hotEffect;
    }

    public void setHotEffect(int hotEffect) {
        this.hotEffect = hotEffect;
    }

    public int getColdEffect() {
        return coldEffect;
    }

    public void setColdEffect(int coldEffect) {
        this.coldEffect = coldEffect;
    }

    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("id", getId().toString());
        json.addProperty("alias", getAlias());
        json.addProperty("password", getPassword());
        json.addProperty("hot-effect", getHotEffect());
        json.addProperty("cold-effect", getColdEffect());
        return json;
    }

}
