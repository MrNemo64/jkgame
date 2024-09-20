package me.nemo_64.sdp.engine.game;

import me.nemo_64.sdp.engine.token.GameTokenCreationRequester;
import me.nemo_64.sdp.utilities.secure.SymmetricCipher;

import javax.crypto.NoSuchPaddingException;
import java.security.NoSuchAlgorithmException;

public class GamePassword implements GameTokenCreationRequester.Password {

    private final String password;

    public GamePassword(String password) {
        this.password = password;
    }

    @Override
    public String serialize() {
        return password;
    }

    @Override
    public SymmetricCipher toCipher(String algorithm) throws NoSuchPaddingException, NoSuchAlgorithmException {
        return SymmetricCipher.create(password);
    }
}
