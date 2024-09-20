package me.nemo_64.sdp.player.socket;

import me.nemo_64.sdp.player.ModifyAccountManager;
import me.nemo_64.sdp.player.util.PlayerData;
import me.nemo_64.sdp.utilities.socket.ClientSocketSession;

import java.io.IOException;
import java.net.Socket;

public class SocketModifyAccountManager extends ModifyAccountManager {

    @Override
    public void modifyAccount(String registryIp, int registryPort) {
        try {
            System.out.println("Input account credentials");
            String credentials = PlayerData.askForCredentials().serializeCredentials();
            System.out.println("Input new account values");
            String data = credentials + ":" + PlayerData.askForData().serialize();
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
            if (session.sendString("edit:" + data).isPresent())
                return;
            var creationResult = session.readString();
            if (creationResult.isError()) {
                System.out.println("Could not check if the player was edited: " + creationResult.error().name());
                return;
            }
            if ("SUCCESS".equals(creationResult.value())) {
                LOGGER.info("Edited user: " + data);
                System.out.println("User edited successfully");
            } else {
                LOGGER.info("Could not edited user: " + creationResult.value());
                System.out.println("Could not edited user: " + creationResult.value());
            }
            session.endSession();
        } catch (IOException e) {
            LOGGER.warning("IO exception while creating socket: " + e.getMessage());
            System.out.println("Could not create socket: " + e.getMessage());
        }
    }

}
