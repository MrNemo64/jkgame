package me.nemo_64.sdp.player;

import me.nemo_64.sdp.player.util.MovementListener;
import me.nemo_64.sdp.utilities.KafkaTopic;
import me.nemo_64.sdp.utilities.secure.SymmetricCipher;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import javax.swing.JFrame;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class OngoingGame {

    private static final ScheduledExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadScheduledExecutor();

    private ScheduledFuture<?> keepAliveSender;
    private final String token;
    private final SymmetricCipher symmetricCipher;
    private final KafkaConsumer<String, String> consumer;
    private final KafkaProducer<String, String> producer;
    private final MovementListener movementListener = new MovementListener(this::handleMovement);
    private final Thread gameLoopThread = new Thread(this::gameLoop);
    private final AtomicBoolean died = new AtomicBoolean(false);
    private boolean gameStarted = false;
    private final CompletableFuture<Void> finishFuture = new CompletableFuture<>();
    private final AtomicLong lastEngineNotification = new AtomicLong(System.currentTimeMillis());

    public OngoingGame(String token, String brokerIp, SymmetricCipher symmetricCipher) {
        this.token = token;
        this.symmetricCipher = symmetricCipher;
        Properties producerProperties = new Properties();
        producerProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerIp);
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        this.producer = new KafkaProducer<>(producerProperties);
        Properties consumerProperties = new Properties();
        consumerProperties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerIp);
        consumerProperties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());
        consumerProperties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());
        consumerProperties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, "AA_Player-" + token);
        consumerProperties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        this.consumer = new KafkaConsumer<>(consumerProperties);
        this.consumer
                .subscribe(Arrays.asList(KafkaTopic.ENGINE_KEEP_ALIVE, KafkaTopic.MAP, KafkaTopic.PLAYER_GAME_UPDATES));
        this.movementListener.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    private void gameLoop() {
        lastEngineNotification.set(System.currentTimeMillis());
        while (!finishFuture.isDone()) {
            var records = consumer.poll(Duration.ofMillis(20));
            if (records.count() > 0)
                lastEngineNotification.set(System.currentTimeMillis());
            if (gameStarted && lastEngineNotification.get() + 1000 < System.currentTimeMillis()) {
                System.out.println("Is the server down?");
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                }
                continue;
            }
            for (var record : records) {
                switch (record.topic()) {
                    case KafkaTopic.ENGINE_KEEP_ALIVE -> {
                    } // IGNORED
                    case KafkaTopic.MAP -> {
                        if (gameStarted)
                            showMap(record.value());
                    }
                    case KafkaTopic.PLAYER_GAME_UPDATES -> {
                        if (gameStarted && record.value().equals("death:" + token)) {
                            keepAliveSender.cancel(true);
                            died.set(true);
                        } else if (record.value().equals("game_stared")) {
                            movementListener.setVisible(true);
                            gameStarted = true;
                        } else if (gameStarted && record.value().startsWith("winner:")) {
                            System.out.println("GAME ENDED, WINNER: " + record.value().substring("winner:".length()));
                            finish();
                            return;
                        }
                    }
                }
            }
        }
    }

    private void finish() {
        finishFuture.complete(null);
        keepAliveSender.cancel(true);
        movementListener.setVisible(false);
    }

    public void play(boolean alreadyStarted) {
        gameStarted = alreadyStarted;
        if (gameStarted)
            movementListener.setVisible(true);
        gameLoopThread.start();
        keepAliveSender = EXECUTOR_SERVICE.scheduleAtFixedRate(this::sendKeepAlive, 0, 500, TimeUnit.MILLISECONDS);
    }

    private void sendKeepAlive() {
        producer.send(new ProducerRecord<>(KafkaTopic.MOVABLE_KEEP_ALIVE, token + ":" + System.currentTimeMillis()));
    }

    private void handleMovement(String s) {
        if (died.get()) {
            finish();
        } else {
            producer.send(new ProducerRecord<>(KafkaTopic.MOVEMENT, token + ":" + s));
        }
    }

    private void showMap(String value) {
        if (finishFuture.isDone())
            return;
        if (died.get()) {
            System.out.println("YOU DIED");
            System.out.println("PRESS ANY KEY TO LEAVE");
        }
        // System.out.print("\033[H\033[2J");
        System.out.print(decrypt(value));
        System.out.flush();
    }

    public String decrypt(String msg) {
        // return msg;
        byte[] receivedBytes = Base64.getDecoder().decode(msg.getBytes(StandardCharsets.UTF_8));
        return new String(symmetricCipher.decrypt(receivedBytes), StandardCharsets.UTF_8);
    }

    public void waitUntilFinish() {
        try {
            finishFuture.get();
        } catch (InterruptedException e) {
        } catch (ExecutionException e) {
        }
    }
}
