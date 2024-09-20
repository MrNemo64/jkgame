package me.nemo_64.sdp.utilities.player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LoadedPlayer extends Player {

    private final PlayerManager manager;

    private boolean aliasChanged = false;

    LoadedPlayer(UUID id, PlayerManager manager, String alias, String password, int hotEffect, int coldEffect) {
        super(id, alias, password, hotEffect, coldEffect);
        this.manager = manager;
    }

    public static LoadedPlayer from(Player value, PlayerManager manager) {
        return new LoadedPlayer(value.getId(), manager, value.getAlias(), value.getPassword(), value.getHotEffect(),
                value.getColdEffect());
    }

    @Override
    public void setAlias(String newAlias) {
        if (!manager.isValidAlias(newAlias))
            return;
        if (manager.getByAlias(newAlias).isPresent())
            return; // alias already being used
        if (manager.changeAliasInIndex(getAlias(), newAlias)) {
            aliasChanged = true;
            super.setAlias(newAlias);
        }
    }

    public boolean didAliasChange() {
        return aliasChanged;
    }

    public CompletableFuture<Boolean> save() {
        return manager.savePlayer(this);
    }

    public Optional<PlayerManager.PlayerEditError> edit(String newAlias, String newPassword, int newHotEffect,
            int newColdEffect) {
        String oldAlias = getAlias();
        if (!manager.isValidAlias(newAlias))
            return Optional.of(PlayerManager.PlayerEditError.INVALID_ALIAS);
        String oldPassword = getPassword();
        int oldHE = getHotEffect();
        int oldCE = getColdEffect();
        if (!oldAlias.equals(newAlias))
            setAlias(newAlias);
        setPassword(newPassword);
        setHotEffect(newHotEffect);
        setColdEffect(newColdEffect);
        try {
            if (!save().get()) {
                // COULD NOT SAVE UNDO CHANGES
                if (!oldAlias.equals(newAlias))
                    setAlias(oldAlias);
                setPassword(oldPassword);
                setHotEffect(oldHE);
                setColdEffect(oldCE);
                return Optional.of(PlayerManager.PlayerEditError.CAN_NOT_SAVE);
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(PlayerManager.PlayerEditError.CAN_NOT_SAVE);
        }
    }

}
