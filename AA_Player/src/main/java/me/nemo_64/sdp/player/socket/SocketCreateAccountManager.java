package me.nemo_64.sdp.player.socket;

import me.nemo_64.sdp.player.CreateAccountManager;
import me.nemo_64.sdp.player.util.PlayerData;
import me.nemo_64.sdp.utilities.socket.ClientSocketSession;

import java.io.IOException;
import java.net.Socket;

public class SocketCreateAccountManager extends CreateAccountManager {

    public void createNewAccount(String registryIp, int registryPort) {
        try {
            String data = PlayerData.askForData().serialize();
            Socket socket = new Socket(registryIp, registryPort);
            var sessionR = ClientSocketSession.createSession(socket, 3, LOGGER,
                    socket.getInetAddress().getHostAddress() + ":" + socket.getPort());
            if (sessionR.isError()) {
                System.out
                        .println("Could not connect with registry: " + sessionR.error() + ": Check logs for more info");
                return;
            }
            var session = sessionR.value();
            if (session.enquire().isPresent())
                return;
            LOGGER.info("Sending registry " + data);
            if (session.sendString("create:" + data).isPresent())
                return;
            var creationResult = session.readString();
            if (creationResult.isError()) {
                System.out.println("Could not check if the player was created: " + creationResult.error().name());
                return;
            }
            if ("SUCCESS".equals(creationResult.value())) {
                LOGGER.info("Created user: " + data);
                System.out.println("User created successfully");
            } else {
                LOGGER.info("Could not create user: " + creationResult.value());
                System.out.println("Could not create user: " + creationResult.value());
            }
            session.endSession();
        } catch (IOException e) {
            LOGGER.warning("IO exception while creating socket: " + e.getMessage());
            System.out.println("Could not create socket: " + e.getMessage());
        }

    }

}
