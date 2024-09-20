package me.nemo_64.sdp.player;

import java.util.logging.Logger;

public abstract class JoinMatchManager {

    protected static final Logger LOGGER = Logger.getLogger(JoinMatchManager.class.getName());

    static {
        LOGGER.setParent(Main.PLAYER_LOGGER);
    }

    public abstract void joinMatch(String engineIp, int enginePort, String brokerIp);

}
