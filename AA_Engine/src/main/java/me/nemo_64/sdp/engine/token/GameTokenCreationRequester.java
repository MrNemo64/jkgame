package me.nemo_64.sdp.engine.token;

import me.nemo_64.sdp.utilities.player.LoadedPlayer;
import me.nemo_64.sdp.utilities.secure.SymmetricCipher;

import javax.crypto.NoSuchPaddingException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

public interface GameTokenCreationRequester {

    void onTokenCreated(String token, LoadedPlayer assignee);

    void onRequestCompleted(Map<String, LoadedPlayer> tokens);

    Password password();

    interface Password {
        String serialize();

        SymmetricCipher toCipher(String algorithm) throws NoSuchPaddingException, NoSuchAlgorithmException;
    }

}
