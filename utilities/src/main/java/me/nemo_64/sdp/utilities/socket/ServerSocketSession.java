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

public class ServerSocketSession extends SocketSession {

    public static Result<ServerSocketSession, SocketSessionCreationError> createSession(Socket socket, int attempts,
            Logger logger, String socketAddress) {
        try {
            return Result.ok(new ServerSocketSession(socket, new DataInputStream(socket.getInputStream()),
                    new DataOutputStream(socket.getOutputStream()), attempts, logger, socketAddress));
        } catch (IOException e) {
            return Result.err(SocketSessionCreationError.GENERIC_IO);
        }
    }

    protected ServerSocketSession(Socket socket, DataInputStream in, DataOutputStream out, int remainingAttempts,
            Logger logger, String socketAddress) {
        super(socket, in, out, remainingAttempts, logger, socketAddress);
    }

    @Override
    public Optional<SocketSessionEnquireError> enquire() {
        if (state != SocketSessionState.WAITING)
            return Optional.of(SocketSessionEnquireError.SESSION_ALREADY_ENQUIRED);

        // Indicate transmission accepted
        while (true) {
            var readEnquire = NetworkUtil.readCharacter(in);
            if (readEnquire.isError()) {
                logger.info("Connection with %s %s while trying to enquire. Connection will be terminated."
                        .formatted(socketAddress,
                                switch (readEnquire.error()) {
                                    case TIME_OUT -> "timed out";
                                    case GENERIC_IO -> "threw an IO exception";
                                }));
                endSession();
                return Optional.of(SocketSessionEnquireError.from(readEnquire.error()));
            }
            if (readEnquire.value() == AsciiTable.ENQ) {
                var sendAck = NetworkUtil.sendCharacter(out, AsciiTable.ACK);
                if (sendAck.isPresent()) {
                    // IO exception while sending ACK
                    logger.warning(
                            "%s while sending Ack when trying to enquire connection with %s. Connection will be terminated."
                                    .formatted(switch (sendAck.get()) {
                                        case TIME_OUT -> "Time out";
                                        case GENERIC_IO -> "An IO exception was thrown";
                                    }, socketAddress));
                    endSession();
                    return Optional.of(SocketSessionEnquireError.GENERIC_IO);
                }
                state = SocketSessionState.ENQUIRED;
                break;
            }

            var sendNAck = NetworkUtil.sendCharacter(out, AsciiTable.NACK);
            if (sendNAck.isPresent()) {
                // IO exception while sending NAck
                logger.warning(
                        "%s while sending NAck when trying to enquire connection with %s. Connection will be terminated."
                                .formatted(switch (sendNAck.get()) {
                                    case TIME_OUT -> "Time out";
                                    case GENERIC_IO -> "An IO exception was thrown";
                                }, socketAddress));
                endSession();
                return Optional.of(SocketSessionEnquireError.GENERIC_IO);
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
