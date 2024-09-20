package me.nemo_64.sdp.utilities.player;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import me.nemo_64.sdp.utilities.Result;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class PlayerManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = Logger.getLogger(PlayerManager.class.getName());
    private static final String INDEX_FILE_NAME = "__index.json";

    public static void setLoggerParent(Logger logger) {
        LOGGER.setParent(logger);
    }

    private final Map<String, UUID> playersIndex = new ConcurrentHashMap<>();
    private final Map<UUID, LoadedPlayer> loadedPlayers = new ConcurrentHashMap<>();
    private final PlayerUtil playerUtil;
    private final Path dataFolder;

    public enum PlayerManagerSetUpError {
        GENERIC_IO,
        COULD_NOT_SET_UP_PLAYERS_FOLDER
    }

    public enum PlayerEditError {
        ALIAS_IN_USE,
        INVALID_ALIAS,
        CAN_NOT_SAVE,
        INVALID_PASSWORD
    }

    public static Result<PlayerManager, PlayerManagerSetUpError> setUp(Path playersFolder, Path allowedCharacters) {
        if (!setUpPlayersFolder(playersFolder))
            return Result.err(PlayerManagerSetUpError.COULD_NOT_SET_UP_PLAYERS_FOLDER);
        if (allowedCharacters == null)
            return Result.ok(new PlayerManager(PlayerUtil.setUp(), playersFolder));
        try (var reader = Files.newBufferedReader(allowedCharacters)) {
            return Result.ok(new PlayerManager(PlayerUtil.setUp(reader), playersFolder));
        } catch (IOException e) {
            LOGGER.warning("Could not set up players folder: " + e.getMessage());
            return Result.err(PlayerManagerSetUpError.GENERIC_IO);
        }
    }

    private static boolean setUpPlayersFolder(Path playersFolder) {
        try {
            if (!Files.exists(playersFolder) || !Files.isDirectory(playersFolder))
                Files.createDirectories(playersFolder);
            Path indexFile = playersFolder.resolve(INDEX_FILE_NAME);
            if (!Files.exists(indexFile) || !Files.isRegularFile(indexFile))
                Files.createFile(indexFile);
            return true;
        } catch (IOException e) {
            LOGGER.warning("Could not set up players folder: " + e.getMessage());
            return false;
        }
    }

    private PlayerManager(PlayerUtil playerUtil, Path dataFolder) {
        this.playerUtil = playerUtil;
        this.dataFolder = dataFolder;
        updateIndex();
    }

    public Result<LoadedPlayer, PlayerEditError> createPlayer(String alias, String password, int hotEffect,
            int coldEffect) {
        if (!isValidAlias(alias))
            return Result.err(PlayerEditError.INVALID_ALIAS);
        if (getPlayer(alias).isPresent()) // already exists a player
            return Result.err(PlayerEditError.ALIAS_IN_USE);
        LoadedPlayer player = new LoadedPlayer(UUID.randomUUID(), this, alias, password, hotEffect, coldEffect);
        try {
            boolean saved = savePlayer(player).join();
            if (!saved) {
                LOGGER.warning("Could not save a new player");
                return Result.err(PlayerEditError.CAN_NOT_SAVE);
            }
            playersIndex.put(alias, player.getId());
            loadedPlayers.put(player.getId(), player);
            saveIndex();
            LOGGER.info("New player created: " + player);
            return Result.ok(player);
        } catch (CompletionException e) {
            LOGGER.warning("Tried to create a new player " + player + " but the saving task threw and exception: "
                    + e.getMessage());
            return Result.err(PlayerEditError.CAN_NOT_SAVE);
        } catch (CancellationException e) {
            LOGGER.warning(
                    "Tried to create a new player " + player + " the saving task was cancelled: " + e.getMessage());
            return Result.err(PlayerEditError.CAN_NOT_SAVE);
        }
    }

    public Optional<LoadedPlayer> getPlayer(String alias) {
        if (!isValidAlias(alias))
            return Optional.empty();
        if (!playersIndex.containsKey(alias))
            updateIndex();
        if (!playersIndex.containsKey(alias))
            return Optional.empty();
        UUID uuid = playersIndex.get(alias);
        return Optional.ofNullable(loadedPlayers.computeIfAbsent(uuid, this::loadPlayer));
    }

    public Optional<LoadedPlayer> getPlayer(UUID id) {
        return Optional.ofNullable(loadedPlayers.computeIfAbsent(id, this::loadPlayer));
    }

    private LoadedPlayer loadPlayer(UUID id) {
        try (Reader reader = Files.newBufferedReader(fileOf(id))) {
            JsonReader jsonReader = GSON.newJsonReader(reader);
            JsonElement element = JsonParser.parseReader(jsonReader);
            if (!element.isJsonObject()) {
                LOGGER.warning("The file of " + id + " does not contain a JSON object");
                return null;
            }
            var deserializationResult = Player.deserialize(element.getAsJsonObject());
            if (deserializationResult.isError()) {
                LOGGER.warning("Could not deserialize " + id + ": " + deserializationResult.error());
                return null;
            }
            return LoadedPlayer.from(deserializationResult.value(), this);
        } catch (JsonSyntaxException e) {
            LOGGER.warning("Tried to parse " + fileOf(id) + " but it does not contain valid JSON");
            return null;
        } catch (SecurityException e) {
            LOGGER.warning("Tried to open " + fileOf(id) + " but a security manager is active");
            return null;
        } catch (JsonIOException | IOException e) {
            LOGGER.warning("IO exception while reading file of " + id + ": " + e.getMessage());
            return null;
        }

    }

    private void saveIndex() {
        JsonObject json = new JsonObject();
        playersIndex.forEach((alias, uuid) -> json.addProperty(alias, uuid.toString()));
        try {
            Files.writeString(dataFolder.resolve(INDEX_FILE_NAME), GSON.toJson(json));
        } catch (IOException e) {
            LOGGER.warning("Could not save index file: " + e.getMessage());
        }
    }

    private void updateIndex() {
        LOGGER.info("Updating index cache from index file");
        try (BufferedReader reader = Files.newBufferedReader(dataFolder.resolve(INDEX_FILE_NAME))) {
            Map<String, UUID> playersIndex = new HashMap<>();
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject()) {
                LOGGER.warning("Invalid index file format");
                return;
            }
            JsonObject obj = element.getAsJsonObject();
            for (var entry : obj.entrySet()) {
                String key = entry.getKey();
                if (!isValidAlias(key)) {
                    LOGGER.warning("Invalid alias on index file: '" + key + "'. Entry will be skipped");
                    continue;
                }
                JsonElement value = entry.getValue();
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                    LOGGER.warning("Invalid entry on the index file, the value is not a string: '" + value
                            + ". Entry will be skipped");
                    continue;
                }
                try {
                    UUID uuid = UUID.fromString(value.getAsString());
                    playersIndex.put(key, uuid);
                } catch (IllegalArgumentException e) {
                    LOGGER.warning("Invalid entry on the index file, the value is not formatted as an UUID: '" + value
                            + "'. Entry will be skipped");
                }
            }
            this.playersIndex.clear();
            this.playersIndex.putAll(playersIndex);
            LOGGER.info("Updated index cache from file");
        } catch (JsonParseException e) {
            LOGGER.warning("Invalid json on the index file: " + e.getMessage());
        } catch (IOException e) {
            LOGGER.warning("Could not read index file: " + e.getMessage());
        }
    }

    public Path fileOf(UUID player) {
        return dataFolder.resolve(player.toString() + ".json");
    }

    public CompletableFuture<Boolean> savePlayer(LoadedPlayer player) {
        return CompletableFuture.supplyAsync(() -> {
            if (player.didAliasChange())
                saveIndex();
            String json = GSON.toJson(player.serialize());
            try {
                Files.writeString(fileOf(player.getId()), json);
                LOGGER.info("Saved file of " + player.getAlias() + "(" + player.getId() + ")");
                return true;
            } catch (IOException e) {
                LOGGER.warning("Could not write file of %s(%s): %s".formatted(player.getAlias(), player.getId(),
                        e.getMessage()));
                return false;
            }
        });
    }

    boolean changeAliasInIndex(String oldAlias, String newAlias) {
        if (playersIndex.containsKey(newAlias))
            return false;
        UUID uuid = playersIndex.remove(oldAlias);
        if (uuid == null)
            return false;
        playersIndex.put(newAlias, uuid);
        return true;
    }

    public boolean isValidAlias(String alias) {
        return !INDEX_FILE_NAME.equals(alias) && playerUtil.isValidAlias(alias);
    }

    public Optional<UUID> getByAlias(String alias) {
        if (!isValidAlias(alias))
            return Optional.empty();
        return Optional.ofNullable(playersIndex.get(alias));
    }

}
