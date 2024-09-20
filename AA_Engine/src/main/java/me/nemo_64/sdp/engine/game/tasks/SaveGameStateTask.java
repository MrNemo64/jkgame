package me.nemo_64.sdp.engine.game.tasks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import me.nemo_64.sdp.engine.game.Game;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TimerTask;

public class SaveGameStateTask extends TimerTask {

    public static final Path GAME_STATE_FILE = Path.of("latestGame.json").toAbsolutePath();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Game game;

    public SaveGameStateTask(Game game) {
        this.game = game;
        game.getLogger().info("Using " + GAME_STATE_FILE.toAbsolutePath() + " as game file.");
    }

    @Override
    public void run() {
        try {
            long start = System.currentTimeMillis();
            JsonObject json = game.asJson();
            if (!Files.exists(GAME_STATE_FILE)) {
                Path parent = GAME_STATE_FILE.getParent();
                if (!Files.exists(parent))
                    Files.createDirectories(parent);
                Files.createFile(GAME_STATE_FILE);
            }
            Files.writeString(GAME_STATE_FILE, GSON.toJson(json));
            long end = System.currentTimeMillis();
            game.getLogger().info("Game state saved on " + GAME_STATE_FILE + ". Took " + (end - start) + "ms to save.");
        } catch (IOException e) {
            System.out.println("IO while saving map: " + e.getMessage());
            game.getLogger().warning("Could not save map file: " + e.getMessage());
        }
    }

}
