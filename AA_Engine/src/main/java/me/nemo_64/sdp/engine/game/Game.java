package me.nemo_64.sdp.engine.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.nemo_64.sdp.engine.Main;
import me.nemo_64.sdp.engine.game.element.FoodGameElement;
import me.nemo_64.sdp.engine.game.element.GameElement;
import me.nemo_64.sdp.engine.game.element.MineGameElement;
import me.nemo_64.sdp.engine.game.element.NPCGameElement;
import me.nemo_64.sdp.engine.game.element.PlayerGameElement;
import me.nemo_64.sdp.engine.game.tasks.CheckDisconnectsTimerTask;
import me.nemo_64.sdp.engine.game.tasks.NotifyGameStarted;
import me.nemo_64.sdp.engine.game.tasks.SaveGameStateTask;
import me.nemo_64.sdp.engine.game.tasks.SendGameMapTask;
import me.nemo_64.sdp.engine.game.tasks.SendKeepAliveMessage;
import me.nemo_64.sdp.engine.util.ConfigurationEntry;
import me.nemo_64.sdp.utilities.KafkaTopic;
import me.nemo_64.sdp.utilities.NumberUtil;
import me.nemo_64.sdp.utilities.configuration.Config;
import me.nemo_64.sdp.utilities.data.City;
import me.nemo_64.sdp.utilities.player.LoadedPlayer;
import me.nemo_64.sdp.utilities.secure.SymmetricCipher;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class Game {

    private static final double FOOD_PROBABILITY = 0.2;
    private static final double MINE_PROBABILITY = 0.15;
    private static final Logger LOGGER = Logger.getLogger(Game.class.getName());
    private static final Random RANDOM = new Random();
    private static final ScheduledExecutorService SERVICE = Executors.newScheduledThreadPool(2);

    static {
        LOGGER.setParent(Main.ENGINE_LOGGER);
    }

    private static KafkaProducer<String, String> createProducer(String bootstrap) {
        Properties producerProperties = new Properties();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return new KafkaProducer<>(producerProperties);
    }

    private static KafkaConsumer<String, String> createConsumer(String bootstrap) {
        Properties consumerProperties = new Properties();
        consumerProperties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        consumerProperties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());
        consumerProperties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());
        consumerProperties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "AA_Engine");
        consumerProperties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties);
        consumer.subscribe(
                Arrays.asList(KafkaTopic.MOVEMENT, KafkaTopic.MOVABLE_KEEP_ALIVE, KafkaTopic.NPC_JOIN_LEAVE));
        return consumer;
    }

    public static Optional<Game> create(Collection<GameElement> elements, City[] cities, String bootstrap,
            SymmetricCipher symmetricCipher) {
        return Optional
                .of(new Game(elements, cities, createProducer(bootstrap), createConsumer(bootstrap), symmetricCipher));
    }

    public static Optional<Game> create(Map<String, LoadedPlayer> tokens, City[] cities, String bootstrap,
            SymmetricCipher symmetricCipher) {
        Set<Position> usedPositions = new HashSet<>();
        List<GameElement> elements = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            for (int j = 0; j < 20; j++) {
                Position p = new Position(i, j);
                if (RANDOM.nextDouble() <= FOOD_PROBABILITY) {
                    elements.add(new FoodGameElement(null, p));
                    usedPositions.add(p);
                } else if (RANDOM.nextDouble() <= MINE_PROBABILITY) {
                    elements.add(new MineGameElement(null, p));
                    usedPositions.add(p);
                }
            }
        }
        tokens.forEach((token, player) -> {
            Position p;
            do {
                p = randomPosition();
            } while (usedPositions.contains(p));
            usedPositions.add(p);
            elements.add(new PlayerGameElement(null, p, player, 1, token));
        });
        return Optional
                .of(new Game(elements, cities, createProducer(bootstrap), createConsumer(bootstrap), symmetricCipher));
    }

    private static Position randomPosition() {
        return new Position(RANDOM.nextInt(20), RANDOM.nextInt(20));
    }

    private static Optional<Position> movementFor(String movement) {
        return Optional.ofNullable(switch (movement) {
            case "NW" -> new Position(-1, -1);
            case "N" -> new Position(0, -1);
            case "NE" -> new Position(1, -1);
            case "W" -> new Position(-1, 0);
            case "E" -> new Position(1, 0);
            case "SW" -> new Position(-1, 1);
            case "S" -> new Position(0, 1);
            case "SE" -> new Position(1, 1);
            default -> null;
        });
    }

    private ScheduledFuture<?> mapSender;
    private ScheduledFuture<?> keepAliveSender;
    private ScheduledFuture<?> disconnectsChecker;
    private ScheduledFuture<?> gameSaver;
    private boolean running = false;
    private final City[] cities;
    private final Map<String, GameElement.MovableGameElement> movable = new ConcurrentHashMap<>();
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final Map<Position, List<GameElement>> elements = new ConcurrentHashMap<>();
    private final KafkaProducer<String, String> producer;
    private final KafkaConsumer<String, String> consumer;
    private final SymmetricCipher symmetricCipher;

    public Game(Collection<GameElement> elements, City[] cities, KafkaProducer<String, String> producer,
            KafkaConsumer<String, String> consumer, SymmetricCipher symmetricCipher) {
        this.cities = cities;
        this.producer = producer;
        this.consumer = consumer;
        this.symmetricCipher = symmetricCipher;
        elements.forEach(this::addElement);
    }

    public List<GameElement> getElementsAt(Position position) {
        return Collections.unmodifiableList(elements.getOrDefault(position, new ArrayList<>()));
    }

    public void addElement(GameElement element) {
        element.assignGame(this);
        if (element instanceof GameElement.MovableGameElement movableGameElement) {
            if (movableGameElement.token() == null) {
                LOGGER.warning("Tried to add a movable game element (" + element + ") but its token is null");
                return;
            }
            movable.put(movableGameElement.token(), movableGameElement);
        }
        elements.computeIfAbsent(element.getPosition(), pos -> new ArrayList<>()).add(element);
    }

    public void removeElement(GameElement element) {
        if (element.getGame() != this)
            return;
        elements.computeIfPresent(element.getPosition(), (key, elements) -> {
            elements.remove(element);
            if (element.isStillAlive())
                element.die();
            element.notifyRemoved();
            return elements.isEmpty() ? null : elements;
        });
        if (element instanceof GameElement.MovableGameElement movableGameElement) {
            movable.remove(movableGameElement.token());
        }
    }

    private void gameLoop() {
        while (!finished.get()) {
            for (var record : consumer.poll(Duration.ofMillis(10))) {
                if (record.topic().equals(KafkaTopic.NPC_JOIN_LEAVE)) {
                    if (!record.value().startsWith("join:"))
                        continue;
                    System.out.println("An NPC requested joining");
                    SERVICE.schedule(() -> acceptNPC(record.value()), 500, TimeUnit.MILLISECONDS);
                    continue;
                }
                String[] message = record.value().split(":");
                if (message.length != 2) {
                    LOGGER.warning("Invalid message on the topic %s: %s. Ignoring record".formatted(record.topic(),
                            record.value()));
                    continue;
                }
                String token = message[0];
                GameElement.MovableGameElement element = movable.get(token);
                if (element == null) {
                    LOGGER.warning("Unknown token received on " + record.topic() + ". Ignoring record");
                    continue;
                }
                switch (record.topic()) {
                    case KafkaTopic.MOVEMENT:
                        move(element, message[1]);
                    case KafkaTopic.MOVABLE_KEEP_ALIVE:
                        element.setLastMovementTimeStamp(System.currentTimeMillis());
                        break;
                }
            }
        }
    }

    private void acceptNPC(String str) {
        if (!str.startsWith("join:")) {
            LOGGER.info("Invalid message on " + KafkaTopic.NPC_JOIN_LEAVE + ": " + str);
            return;
        }
        String[] args = str.split(":");
        if (args.length != 3)
            return;
        Optional<Integer> level = NumberUtil.tryParseInt(args[1]);
        if (level.isEmpty() || level.get() <= 0) {
            LOGGER.info("Invalid level on " + KafkaTopic.NPC_JOIN_LEAVE + ": " + str);
            return;
        }
        String id = args[2];
        String token = "npc-" + new Random().nextInt();
        NPCGameElement npc = new NPCGameElement(this, randomPosition(), level.get(), token);
        LOGGER.info("NPC joined: " + npc);
        System.out.println("NPC joined: " + npc);
        addElement(npc);
        npc.setLastMovementTimeStamp(System.currentTimeMillis());
        producer.send(new ProducerRecord<>(KafkaTopic.NPC_JOIN_LEAVE, "accept:" + id + ":" + token));
    }

    private void move(GameElement.MovableGameElement element, String movementCode) {
        if (!element.isStillAlive())
            return;
        var oMovement = movementFor(movementCode);
        if (oMovement.isEmpty()) {
            LOGGER.info(element.token() + " sent and invalid movement code: " + movementCode);
            return;
        }
        Position oldPosition = element.getPosition();
        Position newPos = oldPosition.plus(oMovement.get().x(), oMovement.get().y()).normalized();
        if (elements.containsKey(newPos)) {
            List<GameElement> elementsInNewPos = new ArrayList<>(elements.get(newPos));
            elementsLoop: for (GameElement other : elementsInNewPos) {
                switch (element.interactWith(other)) {
                    case NOTHING -> {
                    }
                    case REMOVE_BOTH -> {
                        removeElement(element);
                        removeElement(other);
                        break elementsLoop;
                    }
                    case REMOVE_OTHER -> removeElement(other);
                    case REMOVE_SELF -> {
                        removeElement(element);
                        break elementsLoop;
                    }
                }
                if (!element.isStillAlive()) {
                    checkWinner();
                    return;
                }
            }
        }
        elements.computeIfPresent(oldPosition, (key, elements) -> {
            elements.remove(element);
            return elements.isEmpty() ? null : elements;
        });
        if (element.isStillAlive()) {
            elements.computeIfAbsent(newPos, (p) -> new ArrayList<>()).add(element);
            element.setPosition(newPos);
        } else {
            checkWinner();
        }
    }

    private void checkWinner() {
        var remainingPlayers = getMovableElements().stream()
                .filter(PlayerGameElement.class::isInstance)
                .map(PlayerGameElement.class::cast)
                .map(PlayerGameElement::getPlayer).toList();
        long playerCount = remainingPlayers.size();
        if (playerCount == 0) {
            finishGame(null);
        } else if (playerCount == 1) {
            finishGame(remainingPlayers.get(0));
        }
    }

    public void finishGame(LoadedPlayer winner) {
        System.out.println("Finishing game");
        finished.set(true);
        running = false;
        mapSender.cancel(true);
        keepAliveSender.cancel(true);
        gameSaver.cancel(true);
        disconnectsChecker.cancel(true);
        try {
            Files.deleteIfExists(SaveGameStateTask.GAME_STATE_FILE);
        } catch (IOException e) {
            LOGGER.warning("Could not delete game save file: " + e.getMessage());
        }
        String winnerName = winner == null ? "NO WINNER" : winner.getAlias();
        producer.send(new ProducerRecord<>(KafkaTopic.NPC_JOIN_LEAVE, "game_ended"));
        producer.send(new ProducerRecord<>(KafkaTopic.PLAYER_GAME_UPDATES, "winner:" + winnerName));
        producer.flush();
        System.out.println("Game finished");
        SERVICE.schedule(() -> System.exit(0), 5, TimeUnit.SECONDS);
    }

    public void startGame() {
        if (isRunning())
            return;
        LOGGER.info("Starting game in 3 seconds");
        System.out.println("Starting game in 3 seconds");
        running = true;
        mapSender = SERVICE.scheduleAtFixedRate(new SendGameMapTask(this), 0, 17, TimeUnit.MILLISECONDS);
        keepAliveSender = SERVICE.scheduleAtFixedRate(new SendKeepAliveMessage(this), 0, 1000, TimeUnit.MILLISECONDS);
        gameSaver = SERVICE.scheduleAtFixedRate(new SaveGameStateTask(this), 250,
                Config.getOptionalInt(ConfigurationEntry.SAVE_MAP_PERIOD).orElse(17), TimeUnit.MILLISECONDS);
        disconnectsChecker = SERVICE.scheduleAtFixedRate(new CheckDisconnectsTimerTask(this), 3750, 1000,
                TimeUnit.MILLISECONDS);
        SERVICE.schedule(new NotifyGameStarted(this), 3, TimeUnit.SECONDS);
        gameLoop();
    }

    public JsonObject asJson() {
        JsonObject json = new JsonObject();
        json.add("cities", citiesAsJson());
        json.add("map", mapAsJson());
        json.add("password", symmetricCipher.serialize());
        return json;
    }

    public JsonArray citiesAsJson() {
        City[] cities = getCities();
        JsonArray array = new JsonArray(4);
        for (City citi : cities) {
            array.add(citi.asJson());
        }
        return array;
    }

    public JsonArray mapAsJson() {
        return getElements()
                .values()
                .stream()
                .flatMap(List::stream)
                .filter(GameElement::shouldBeSaved)
                .reduce(new JsonArray(), (array, element) -> {
                    array.add(element.asJson());
                    return array;
                }, (array1, array2) -> {
                    JsonArray array = new JsonArray(array1.size() + array2.size());
                    array.addAll(array1);
                    array.addAll(array2);
                    return array;
                });
    }

    public Collection<GameElement.MovableGameElement> getMovableElements() {
        return Collections.unmodifiableCollection(movable.values());
    }

    public boolean isRunning() {
        return running;
    }

    public KafkaProducer<String, String> getProducer() {
        return producer;
    }

    public City getCity(int x, int y) {
        int column = x / 10;
        int row = y / 10;
        return cities[column + row * 2];
    }

    public Map<Position, List<GameElement>> getElements() {
        return elements;
    }

    public City[] getCities() {
        return cities;
    }

    public Logger getLogger() {
        return LOGGER;
    }

    public SymmetricCipher getSymmetricCipher() {
        return symmetricCipher;
    }
}
