package me.nemo_64.sdp.npc;

import me.nemo_64.sdp.utilities.KafkaTopic;
import me.nemo_64.sdp.utilities.NumberUtil;
import me.nemo_64.sdp.utilities.configuration.Config;
import me.nemo_64.sdp.utilities.configuration.ConfigurationBuilder;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Main {

    private static final Logger NPC_LOGGER = Logger.getLogger("AA_NPC");
    private static final ScheduledExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadScheduledExecutor();
    private static final String[] MOVEMENTS = { "NW", "N", "NE", "W", "E", "SW", "S", "SE" };
    private static final UUID ID = UUID.randomUUID();

    private static KafkaProducer<String, String> producer;
    private static KafkaConsumer<String, String> consumer;
    private static ScheduledFuture<?> keepAliveSender;
    private static String token;

    static {
        // NPC_LOGGER.setUseParentHandlers(false);
        try {
            Path file = Paths.get("log/AA_NPC." + (System.currentTimeMillis() / 1000) + ".log");
            Path parent = file.getParent();
            if (!Files.exists(parent))
                Files.createDirectories(parent);
            Files.createFile(file);
            FileHandler handler = new FileHandler(file.toString());
            handler.setFormatter(new SimpleFormatter());
            NPC_LOGGER.addHandler(handler);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void sendKeepAlive() {
        producer.send(new ProducerRecord<>(KafkaTopic.MOVABLE_KEEP_ALIVE, token + ":" + System.currentTimeMillis()));
    }

    private static void run() {
        keepAliveSender = EXECUTOR_SERVICE.scheduleAtFixedRate(Main::sendKeepAlive, 0, 700, TimeUnit.MILLISECONDS);
        Random r = new Random();
        String deathMessage = "die:" + token;
        long lastServerMessage = System.currentTimeMillis();
        while (true) {
            var records = consumer.poll(Duration.ofMillis(r.nextInt(500, 4500)));
            if (records.count() > 0)
                lastServerMessage = System.currentTimeMillis();
            if (lastServerMessage + 1000 < System.currentTimeMillis()) {
                System.out.println("Is the server down?");
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                }
                continue;
            }
            for (var message : records) {
                if (KafkaTopic.ENGINE_KEEP_ALIVE.equals(message.topic()))
                    continue;
                if (message.value().equals(deathMessage)) {
                    System.out.println("DIED");
                    System.exit(0);
                } else if (message.value().equals("game_ended")) {
                    System.out.println("GAME FINISHED");
                    EXECUTOR_SERVICE.schedule(() -> System.exit(0), 5, TimeUnit.SECONDS);
                }
            }
            String movement = MOVEMENTS[r.nextInt(MOVEMENTS.length)];
            System.out.println("MOVING " + movement);
            producer.send(new ProducerRecord<>(KafkaTopic.MOVEMENT, token + ":" + movement));
            producer.flush();
        }
    }

    public static void main(String[] args) {
        if (args.length != 1 && args.length != 2) {
            System.out.println("Invalid amount of arguments. Usage: AA_NPC <config> [token]");
            System.exit(-1);
        }
        var configBuilder = ConfigurationBuilder.newBuilder()
                .registerPrimitiveParsers()
                .register("broker-ip", String.class, false)
                .register("level", Integer.TYPE, false, 5, NumberUtil::isGraterThanZero)
                .withLogger(NPC_LOGGER);
        if (args.length == 1) {
            configBuilder.withFile(Path.of(args[0]));
        }
        var conf = configBuilder.create();
        if (conf.isError()) {
            System.out.println("Could not load config: " + conf.error().message());
            System.exit(-1);
        }
        Config.setInstance(conf.value());
        NPC_LOGGER.info("Using the following configuration:" + Config.getInstance().display("  "));

        Properties producerProperties = new Properties();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Config.getString("broker-ip"));
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producer = new KafkaProducer<>(producerProperties);
        Properties consumerProperties = new Properties();
        consumerProperties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, Config.getString("broker-ip"));
        consumerProperties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());
        consumerProperties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());
        consumerProperties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "AA_NPC-" + ID);
        consumerProperties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        consumer = new KafkaConsumer<>(consumerProperties);
        consumer.subscribe(Arrays.asList(KafkaTopic.NPC_JOIN_LEAVE, KafkaTopic.ENGINE_KEEP_ALIVE));
        if (args.length == 2) {
            token = args[1];
        } else {
            if (!requestToken(Config.getInt("level"))) {
                System.out.println("Could not get token, server did no respond");
                System.exit(-1);
            }
        }
        NPC_LOGGER.info("Using " + token + " as token");
        run();
    }

    private static boolean requestToken(int level) {
        producer.send(new ProducerRecord<>(KafkaTopic.NPC_JOIN_LEAVE, "join:" + level + ":" + ID));
        System.out.println("Requested token");
        String expectedMessageStart = "accept:" + ID + ":";
        for (var message : consumer.poll(Duration.ofMillis(6000))) {
            System.out.println(message.value());
            if (message.value().startsWith(expectedMessageStart)) {
                token = message.value().substring(expectedMessageStart.length());
                return true;
            }
        }
        return false;
    }

}
