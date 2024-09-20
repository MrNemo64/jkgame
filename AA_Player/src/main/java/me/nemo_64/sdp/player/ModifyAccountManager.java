package me.nemo_64.sdp.player;

import java.util.logging.Logger;

public abstract class ModifyAccountManager {

    protected static final Logger LOGGER = Logger.getLogger(CreateAccountManager.class.getName());

    static {
        LOGGER.setParent(Main.PLAYER_LOGGER);
    }

    public abstract void modifyAccount(String registryIp, int registryPort);
}
