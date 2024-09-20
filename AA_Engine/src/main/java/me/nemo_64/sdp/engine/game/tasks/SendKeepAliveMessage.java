package me.nemo_64.sdp.engine.game.tasks;

import me.nemo_64.sdp.engine.game.Game;
import me.nemo_64.sdp.utilities.KafkaTopic;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.TimerTask;

public class SendKeepAliveMessage extends TimerTask {

    private final Game game;

    public SendKeepAliveMessage(Game game) {
        this.game = game;
    }

    @Override
    public void run() {
        ProducerRecord<String, String> mapRecord = new ProducerRecord<>(KafkaTopic.ENGINE_KEEP_ALIVE,
                String.valueOf(System.currentTimeMillis()));
        game.getProducer().send(mapRecord, (data, ex) -> {
            if (ex != null) {
                game.getLogger().warning("Could not send keep alive:" + ex.getMessage());
            } else {
                game.getLogger().info("Notified server still alive");
            }
        });
    }
}
