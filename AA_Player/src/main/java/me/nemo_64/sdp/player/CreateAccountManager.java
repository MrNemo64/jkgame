package me.nemo_64.sdp.player;

import java.util.logging.Logger;

public abstract class CreateAccountManager {

    protected static final Logger LOGGER = Logger.getLogger(CreateAccountManager.class.getName());

    static {
        LOGGER.setParent(Main.PLAYER_LOGGER);
    }

    public abstract void createNewAccount(String registryIp, int registryPort);

}