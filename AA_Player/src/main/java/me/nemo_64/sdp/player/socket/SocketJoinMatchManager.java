package me.nemo_64.sdp.player.socket;

import me.nemo_64.sdp.player.JoinMatchManager;
import me.nemo_64.sdp.player.OngoingGame;
import me.nemo_64.sdp.player.util.ConfigurationEntry;
import me.nemo_64.sdp.player.util.PlayerData;
import me.nemo_64.sdp.utilities.configuration.Config;
import me.nemo_64.sdp.utilities.secure.SymmetricCipher;
import me.nemo_64.sdp.utilities.socket.ClientSocketSession;

import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;

public class SocketJoinMatchManager extends JoinMatchManager {

    @Override
    public void joinMatch(String engineIp, int enginePort, String brokerIp) {
        PlayerData credentials = PlayerData.askForCredentials();

        try {
            Socket socket = new Socket(engineIp, enginePort);
            var sessionResult = ClientSocketSession.createSession(socket, 3, LOGGER,
                    socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
            if (sessionResult.isError()) {
                LOGGER.warning("Could not create session: " + sessionResult.error());
                System.out.println("Could not create session: " + sessionResult.error());
                return;
            }
            ClientSocketSession session = sessionResult.value();
            if (session.enquire().isPresent()) {
                System.out.println("Could not enquire connection with engine");
                return;
            }
            if (session.sendString(credentials.alias() + ":" + credentials.password()).isPresent()) {
                System.out.println("Could not send credentials to engine");
                return;
            }
            var engineResponse = session.readString();
            if (engineResponse.isError()) {
                System.out.println("Could not receive engine response: " + engineResponse.error());
                return;
            }
            session.endSession();
            switch (engineResponse.value()) {
                case "UNKNOWN_USER" -> {
                    System.out.printf("There is no user with %s as alias%n", credentials.alias());
                    return;
                }
                case "INCORRECT_PASSWORD" -> {
                    System.out.println("Incorrect password");
                    return;
                }
                case "NOT_SERVING_TOKENS" -> {
                    System.out.println("There are no matches waiting for players");
                    return;
                }
            }
            String[] response = engineResponse.value().split(":", 2);
            String password = response[0];
            String token = response[1];

            LOGGER.info("Received " + token + " as token and " + password + " as password");
            SymmetricCipher symmetricCipher = null;
            try {
                symmetricCipher = SymmetricCipher.create(password);
            } catch (NoSuchPaddingException | NoSuchAlgorithmException e) {
                LOGGER.warning(
                        "A theoretically impossible exception was thrown: " + e.getClass() + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
            System.out.println("Found a game. Connecting to game");
            OngoingGame game = new OngoingGame(token, brokerIp, symmetricCipher);
            game.play(false);
            game.waitUntilFinish();
        } catch (IOException e) {
            LOGGER.warning("Could not create socket to ask for token: " + e.getMessage());
            System.out.println("Could not create socket to ask for token: " + e.getMessage());
        }

    }

}
