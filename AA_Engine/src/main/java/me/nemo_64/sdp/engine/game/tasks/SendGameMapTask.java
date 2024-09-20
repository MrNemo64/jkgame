package me.nemo_64.sdp.engine.game.tasks;

import me.nemo_64.sdp.engine.game.Game;
import me.nemo_64.sdp.engine.game.Position;
import me.nemo_64.sdp.engine.game.element.GameElement;
import me.nemo_64.sdp.utilities.KafkaTopic;
import me.nemo_64.sdp.utilities.data.City;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.TimerTask;

public class SendGameMapTask extends TimerTask {

    private final Game game;

    public SendGameMapTask(Game game) {
        this.game = game;
    }

    @Override
    public void run() {
        ProducerRecord<String, String> mapRecord = new ProducerRecord<>(KafkaTopic.MAP, serializeMap());
        game.getProducer().send(mapRecord, (data, ex) -> {
            if (ex != null) {
                game.getLogger().warning("Could not send map update:" + ex.getMessage());
                ex.printStackTrace();
                System.exit(-1);
            } else {
                // game.getLogger().info("Sent map update");
            }
        });
        // game.getProducer().flush();
    }

    private String serializeMap() {
        String map = "    1  2  3  4  5  6  7  8  9  10 11 12 13 14 15 16 17 18 19 20\n";
        for (int row = 0; row < 20; row++) {
            String rowNumber = String.valueOf(row + 1);
            if (rowNumber.length() == 1) {
                rowNumber = " " + rowNumber + " ";
            } else {
                rowNumber = rowNumber + " ";
            }
            map += rowNumber;
            for (int column = 0; column < 20; column++) {
                List<GameElement> elements = game.getElementsAt(new Position(column, row));
                map += elements.isEmpty() ? "   " : elements.get(0).mapRepresentation();
            }
            map += "\n";
        }
        City[] cities = game.getCities();
        for (City city : cities)
            map += city.prettyPrint() + "\n";
        Collection<GameElement.MovableGameElement> movables = game.getMovableElements();
        for (var movable : movables)
            map += movable.display() + "\n";
        byte[] encrypted = game.getSymmetricCipher().encrypt(map.getBytes(StandardCharsets.UTF_8));
        return new String(Base64.getEncoder().encode(encrypted), StandardCharsets.UTF_8);
    }

}
