package me.nemo_64.sdp.registry;

import me.nemo_64.sdp.utilities.NumberUtil;
import me.nemo_64.sdp.utilities.Result;
import me.nemo_64.sdp.utilities.player.LoadedPlayer;
import me.nemo_64.sdp.utilities.player.PlayerManager;
import me.nemo_64.sdp.utilities.socket.ServerSocketSession;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.logging.Logger;

public class RegistryService {

    private static final Logger LOGGER = Logger.getLogger(RegistryService.class.getName());

    static {
        LOGGER.setParent(Main.REGISTRY_LOGGER);
    }

    public enum RegistryServiceCreationError {
        ILLEGAL_PORT,
        SECURITY_MANAGER_DOES_NOT_ALLOW,
        IO_EXCEPTION
    }

    public static Result<RegistryService, RegistryServiceCreationError> createOnPort(int port, int attempts,
            int timeOut, PlayerManager playerManager) {
        ServerSocket server;
        try {
            server = new ServerSocket(port);
        } catch (IOException e) {
            return Result.err(RegistryServiceCreationError.IO_EXCEPTION);
        } catch (IllegalArgumentException e) {
            return Result.err(RegistryServiceCreationError.ILLEGAL_PORT);
        } catch (SecurityException e) {
            return Result.err(RegistryServiceCreationError.SECURITY_MANAGER_DOES_NOT_ALLOW);
        }
        return Result.ok(new RegistryService(server,
                playerManager,
                Main.REGISTRY_EXECUTORS,
                Thread::new,
                (s) -> s.getInetAddress().getHostAddress() + ":" + s.getPort(),
                (s) -> attempts,
                timeOut));
    }

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ServerSocket server;
    private final PlayerManager playerManager;
    private final ExecutorService clientHandlers;
    private final Function<Runnable, Thread> listeningThreadGenerator;
    private final Function<Socket, String> addressFormatter;
    private final ToIntFunction<Socket> socketAttemptsGenerator;
    private final int timeOut;
    private Thread listeningThread;

    private RegistryService(ServerSocket server,
            PlayerManager playerManager,
            ExecutorService clientHandlers,
            Function<Runnable, Thread> listeningThreadGenerator,
            Function<Socket, String> addressFormatter,
            ToIntFunction<Socket> socketAttemptsGenerator,
            int timeOut) {
        this.server = server;
        this.playerManager = playerManager;
        this.clientHandlers = clientHandlers;
        this.listeningThreadGenerator = listeningThreadGenerator;
        this.addressFormatter = addressFormatter;
        this.socketAttemptsGenerator = socketAttemptsGenerator;
        this.timeOut = timeOut;
    }

    private void listenForClients() {
        while (isRunning()) {
            try {
                Socket socket = server.accept();
                socket.setSoTimeout(timeOut);
                LOGGER.info("Client connected on %s with a read time out of %d"
                        .formatted(addressFormatter.apply(socket), timeOut));
                handleClient(socket);
            } catch (SocketTimeoutException | SocketException ignored) {
            } catch (IOException e) {
                LOGGER.warning("An IO exception occurred while accepting a client: %s".formatted(e.getMessage()));
            }
        }
    }

    private void handleClient(Socket socket) {
        clientHandlers.submit(() -> {
            String socketAddress = addressFormatter.apply(socket);
            var serviceResult = ServerSocketSession.createSession(socket,
                    socketAttemptsGenerator.applyAsInt(socket),
                    LOGGER,
                    socketAddress);
            if (serviceResult.isError()) {
                LOGGER.warning("Could not create session for " + socketAddress + ": " + serviceResult.error());
                return;
            }
            ServerSocketSession session = serviceResult.value();

            if (session.enquire().isPresent())
                return;

            var request = session.readString();
            if (request.isError())
                return;
            LOGGER.info(socketAddress + " sent " + request.value());
            String[] receivedRequest = request.value().split(":");
            if (receivedRequest.length < 1) {
                LOGGER.info(socketAddress + " sent invalid information: " + request.value());
                session.sendString("INVALID_INFO");
                session.endSession();
                return;
            }
            switch (receivedRequest[0]) {
                case "create" ->
                    handleCreateUser(session, Arrays.copyOfRange(receivedRequest, 1, receivedRequest.length));
                case "edit" -> handleEditUser(session, Arrays.copyOfRange(receivedRequest, 1, receivedRequest.length));
                default -> {
                    LOGGER.info(socketAddress + " sent invalid information: " + request.value());
                    session.sendString("INVALID_INFO");
                    session.endSession();
                    return;
                }
            }
        });
    }

    private void handleCreateUser(ServerSocketSession session, String[] args) {
        if (args.length != 4) {
            session.sendString("INVALID_INFO:NOT_ENOUGH_ARGS");
            session.endSession();
            return;
        }
        String alias = args[0];
        String password = args[1];
        Optional<Integer> hotEffect = NumberUtil.tryParseInt(args[2]);
        if (hotEffect.isEmpty()) {
            session.sendString("INVALID_INFO:INVALID_HE");
            session.endSession();
            return;
        }
        Optional<Integer> coldEffect = NumberUtil.tryParseInt(args[3]);
        if (coldEffect.isEmpty()) {
            session.sendString("INVALID_INFO:INVALID_CE");
            session.endSession();
            return;
        }
        var result = playerManager.createPlayer(alias, password, hotEffect.get(), coldEffect.get());
        if (result.isError()) {
            session.sendString("ERROR:" + result.error());
        } else {
            session.sendString("SUCCESS");
        }
        session.endSession();
    }

    private void handleEditUser(ServerSocketSession session, String[] args) {
        if (args.length != 6) {
            session.sendString("INVALID_INFO:NOT_ENOUGH_ARGS");
            session.endSession();
            return;
        }
        String oldAlias = args[0];
        String oldPassword = args[1];
        String newAlias = args[2];
        String newPassword = args[3];
        Optional<Integer> newHotEffect = NumberUtil.tryParseInt(args[4]);
        if (newHotEffect.isEmpty()) {
            session.sendString("INVALID_INFO:INVALID_HE");
            session.endSession();
            return;
        }
        Optional<Integer> newColdEffect = NumberUtil.tryParseInt(args[5]);
        if (newColdEffect.isEmpty()) {
            session.sendString("INVALID_INFO:INVALID_CE");
            session.endSession();
            return;
        }
        Optional<LoadedPlayer> oPlayer = playerManager.getPlayer(oldAlias);
        if (oPlayer.isEmpty()) {
            session.sendString("ERROR:NO_USER");
            session.endSession();
            return;
        }
        if (!oldPassword.equals(oPlayer.get().getPassword())) {
            session.sendString("ERROR:INVALID_PASSWORD");
            session.endSession();
            return;
        }
        if (!playerManager.isValidAlias(newAlias)) {
            session.sendString("ERROR:INVALID_ALIAS");
            session.endSession();
            return;
        }
        var result = oPlayer.get().edit(newAlias, newPassword, newHotEffect.get(), newColdEffect.get());
        if (result.isEmpty()) {
            session.sendString("SUCCESS");
        } else {
            session.sendString("ERROR:" + result.get().name());
        }
        session.endSession();
    }

    public boolean startService() {
        if (listeningThread != null) {
            return false;
        }
        listeningThread = listeningThreadGenerator.apply(this::listenForClients);
        listeningThread.start();
        running.set(true);
        return true;
    }

    public boolean isRunning() {
        return running.get();
    }

}
