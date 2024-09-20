package me.nemo_64.sdp.utilities.socket;

import me.nemo_64.sdp.utilities.AsciiTable;
import me.nemo_64.sdp.utilities.IntReference;
import me.nemo_64.sdp.utilities.NetworkUtil;
import me.nemo_64.sdp.utilities.Result;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Optional;
import java.util.logging.Logger;

public abstract class SocketSession {

    public enum SocketSessionCreationError {
        GENERIC_IO
    }

    public enum SocketSessionEnquireError {
        SESSION_ALREADY_ENQUIRED,
        TIME_OUT,
        OUT_OF_ATTEMPTS,
        GENERIC_IO;

        public static SocketSessionEnquireError from(NetworkUtil.PrimitiveError err) {
            return switch (err) {
                case GENERIC_IO -> GENERIC_IO;
                case TIME_OUT -> TIME_OUT;
            };
        }

    }

    public enum SocketSessionPrimitiveError {

        TIME_OUT,
        GENERIC_IO, NOT_ENQUIRED;

        public static SocketSessionPrimitiveError from(NetworkUtil.PrimitiveError err) {
            return switch (err) {
                case GENERIC_IO -> GENERIC_IO;
                case TIME_OUT -> TIME_OUT;
            };
        }

    }

    public enum SocketSessionComplexError {

        NOT_ENQUIRED,
        GENERIC_IO,
        TIME_OUT,
        OUT_OF_ATTEMPTS,
        RECEIVED_EOT;

        public static SocketSessionComplexError from(NetworkUtil.ComplexError err) {
            return switch (err) {
                case GENERIC_IO -> GENERIC_IO;
                case TIME_OUT -> TIME_OUT;
                case OUT_OF_ATTEMPTS -> OUT_OF_ATTEMPTS;
                case RECEIVED_EOT -> RECEIVED_EOT;
            };
        }

    }

    protected final Socket socket;
    protected final DataOutputStream out;
    protected final DataInputStream in;
    protected final IntReference remainingAttempts;
    protected final Logger logger;
    protected final String socketAddress;
    protected SocketSessionState state = SocketSessionState.WAITING;

    protected SocketSession(Socket socket, DataInputStream in, DataOutputStream out, int remainingAttempts,
            Logger logger, String socketAddress) {
        this.socket = socket;
        this.out = out;
        this.in = in;
        this.remainingAttempts = IntReference.from(remainingAttempts);
        this.logger = logger;
        this.socketAddress = socketAddress;
    }

    public abstract Optional<SocketSessionEnquireError> enquire();

    public Optional<SocketSessionPrimitiveError> sendNack() {
        if (state != SocketSessionState.ENQUIRED)
            return Optional.of(SocketSessionPrimitiveError.NOT_ENQUIRED);
        var result = NetworkUtil.sendCharacter(out, AsciiTable.NACK);
        if (result.isPresent()) {
            logger.warning("Could not send Nack to %s because the connection %s. Connection will be terminated."
                    .formatted(socketAddress, switch (result.get()) {
                        case GENERIC_IO -> "threw an IO exception";
                        case TIME_OUT -> "timed out";
                    }));
            endSession();
            return Optional.of(SocketSessionPrimitiveError.from(result.get()));
        }
        return Optional.empty();
    }

    public Optional<SocketSessionComplexError> sendString(String str) {
        if (state != SocketSessionState.ENQUIRED)
            return Optional.of(SocketSessionComplexError.NOT_ENQUIRED);
        var result = NetworkUtil.sendString(in, out, str, remainingAttempts);
        if (result.isPresent()) {
            logger.warning("Could not send '%s' to %s because the connection %s. Connection will be terminated."
                    .formatted(str, socketAddress, switch (result.get()) {
                        case GENERIC_IO -> "threw an IO exception";
                        case TIME_OUT -> "timed out";
                        case OUT_OF_ATTEMPTS -> "ran out of attempts";
                        case RECEIVED_EOT -> "received and EOT";
                    }));
            endSession();
            return Optional.of(SocketSessionComplexError.from(result.get()));
        }
        return Optional.empty();
    }

    public Result<String, SocketSessionComplexError> readString() {
        if (state != SocketSessionState.ENQUIRED)
            return Result.err(SocketSessionComplexError.NOT_ENQUIRED);
        var result = NetworkUtil.readString(in, out, remainingAttempts);
        if (result.isSuccessful())
            return Result.ok(result.value());
        logger.warning(
                "%s while reading a string from %s. Connection will be terminated.".formatted(switch (result.error()) {
                    case TIME_OUT -> "Time out";
                    case GENERIC_IO -> "IO exception was thrown";
                    case OUT_OF_ATTEMPTS -> "Used all attempts";
                    case RECEIVED_EOT -> "received and EOT";
                }, socketAddress));
        endSession();
        return Result.err(SocketSessionComplexError.from(result.error()));
    }

    public Optional<SocketSessionComplexError> sendInt(int i) {
        // TODO
        throw new UnsupportedOperationException();
    }

    public Result<Integer, SocketSessionPrimitiveError> readInt() {
        // TODO
        throw new UnsupportedOperationException();
    }

    public int decrementAttemptsAndGet() {
        int at = remainingAttempts.decrementAndGet();
        if (at == 0)
            endSession();
        return at;
    }

    public void endSession() {
        if (state != SocketSessionState.ENQUIRED)
            return;
        var result = NetworkUtil.sendCharacter(out, AsciiTable.EOT);
        if (result.isPresent()) {
            logger.warning("%s while sending EOT to %s. Connection will be assumed to be closed."
                    .formatted(switch (result.get()) {
                        case TIME_OUT -> "Time out";
                        case GENERIC_IO -> "IO exception was thrown";
                    }, socketAddress));
        }
        try {
            if (!socket.isInputShutdown())
                socket.shutdownInput();
        } catch (IOException e) {
            logger.warning("IO exception while trying to close the input of the socket %s: %s.".formatted(socketAddress,
                    e.getMessage()));
        }
        try {
            if (!socket.isOutputShutdown())
                socket.shutdownOutput();
        } catch (IOException e) {
            logger.warning("IO exception while trying to close the output of the socket %s: %s."
                    .formatted(socketAddress, e.getMessage()));
        }
        try {
            if (!socket.isClosed())
                socket.close();
        } catch (IOException e) {
            logger.warning(
                    "IO exception while trying to close the socket %s: %s.".formatted(socketAddress, e.getMessage()));
        }
        logger.info("Connection with %s closed.".formatted(socketAddress));
        state = SocketSessionState.FINISHED;
    }

    public int remainingAttempts() {
        return remainingAttempts.get();
    }

    public SocketSessionState getState() {
        return state;
    }

    public boolean didSessionEnd() {
        return state == SocketSessionState.FINISHED;
    }
}
