package me.nemo_64.sdp.player.util;

import com.google.gson.JsonObject;
import me.nemo_64.sdp.utilities.secure.Hasher;

public record PlayerData(String alias, String password, int hotEffect, int coldEffect) {

    public static PlayerData askForData() {
        PlayerData credentials = askForCredentials();
        System.out.print("Hot effect: ");
        int he = ConsoleUtil.readInt((nan) -> System.out.println(nan + " is not a number"));
        System.out.print("Cold effect: ");
        int ce = ConsoleUtil.readInt((nan) -> System.out.println(nan + " is not a number"));
        return new PlayerData(credentials.alias, credentials.password, he, ce);
    }

    public static PlayerData askForCredentials() {
        System.out.print("Alias: ");
        String alias = ConsoleUtil.IN.nextLine();
        System.out.print("Password: ");
        String password = Hasher.hash(ConsoleUtil.IN.nextLine());
        return new PlayerData(alias, password, 0, 0);
    }

    public String serializeCredentials() {
        return alias + ":" + password;
    }

    public String serialize() {
        return alias + ":" + password + ":" + hotEffect + ":" + coldEffect;
    }

    public JsonObject asJson() {
        JsonObject json = new JsonObject();
        json.addProperty("alias", alias());
        json.addProperty("password", password());
        json.addProperty("hot-effect", hotEffect());
        json.addProperty("cold-effect", coldEffect());
        return json;
    }

}
