package me.nemo_64.sdp.engine.game.tasks;

import me.nemo_64.sdp.engine.game.Game;
import me.nemo_64.sdp.engine.game.element.GameElement;

import java.util.Collection;
import java.util.TimerTask;

public class CheckDisconnectsTimerTask extends TimerTask {

    private final Game game;

    public CheckDisconnectsTimerTask(Game game) {
        this.game = game;
    }

    @Override
    public void run() {
        Collection<GameElement.MovableGameElement> movable = game.getMovableElements();
        long consideredDisconnected = System.currentTimeMillis() - 1000;
        for (GameElement.MovableGameElement element : movable) {
            if (element.lastMovementTimeStamp() < consideredDisconnected) {
                if (element.isStillAlive() && !element.isDisconnected()) {
                    element.setDisconnected(true);
                    System.out.println(element.token() + " seems to have disconnected");
                    game.getLogger().info(element.token() + " seems to have disconnected.");
                }
            } else if (element.isStillAlive() && element.isDisconnected()) {
                element.setDisconnected(false);
                System.out.println(element.token() + " seems to have reconnected");
                game.getLogger().info(element.token() + " seems to have reconnected");
            }
        }
    }

}
