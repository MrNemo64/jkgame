package me.nemo_64.sdp.engine.game.tasks;

import me.nemo_64.sdp.engine.game.Game;
import me.nemo_64.sdp.utilities.KafkaTopic;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.TimerTask;

public class NotifyGameStarted extends TimerTask {

    private final Game game;

    public NotifyGameStarted(Game game) {
        this.game = game;
    }

    @Override
    public void run() {
        game.getProducer().send(new ProducerRecord<>(KafkaTopic.PLAYER_GAME_UPDATES, "game_stared"));
    }
}
