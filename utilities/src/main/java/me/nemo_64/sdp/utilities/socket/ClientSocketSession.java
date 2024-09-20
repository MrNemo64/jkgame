package me.nemo_64.sdp.utilities.socket;

import me.nemo_64.sdp.utilities.AsciiTable;
import me.nemo_64.sdp.utilities.NetworkUtil;
import me.nemo_64.sdp.utilities.Result;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Optional;
import java.util.logging.Logger;

public class ClientSocketSession extends SocketSession {

    public static Result<ClientSocketSession, SocketSessionCreationError> createSession(Socket socket, int attempts,
            Logger logger, String socketAddress) {
        try {
            return Result.ok(new ClientSocketSession(socket, new DataInputStream(socket.getInputStream()),
                    new DataOutputStream(socket.getOutputStream()), attempts, logger, socketAddress));
        } catch (IOException e) {
            return Result.err(SocketSessionCreationError.GENERIC_IO);
        }
    }

    protected ClientSocketSession(Socket socket, DataInputStream in, DataOutputStream out, int remainingAttempts,
            Logger logger, String socketAddress) {
        super(socket, in, out, remainingAttempts, logger, socketAddress);
    }

    @Override
    public Optional<SocketSessionEnquireError> enquire() {
        if (state != SocketSessionState.WAITING)
            return Optional.of(SocketSessionEnquireError.SESSION_ALREADY_ENQUIRED);
        while (true) {
            var sendEnq = NetworkUtil.sendCharacter(out, AsciiTable.ENQ);
            if (sendEnq.isPresent()) {
                logger.warning(
                        "Could not enquire connection with %s: %s".formatted(socketAddress, sendEnq.get().name()));
                endSession();
                return Optional.of(SocketSessionEnquireError.from(sendEnq.get()));
            }

            var readChar = NetworkUtil.readCharacter(in);
            if (readChar.isError()) {
                logger.warning(
                        "Could not enquire connection with %s: %s".formatted(socketAddress, readChar.error().name()));
                endSession();
                return Optional.of(SocketSessionEnquireError.from(readChar.error()));
            }

            if (readChar.value() == AsciiTable.ACK) {
                state = SocketSessionState.ENQUIRED;
                break;
            }

            if (remainingAttempts.decrementAndGet() == 0) {
                logger.info("%s used all of the attempts while trying to enquire. Connection will be terminated."
                        .formatted(socketAddress));
                endSession();
                return Optional.of(SocketSessionEnquireError.OUT_OF_ATTEMPTS);
            }
        }
        return Optional.empty();
    }
}
