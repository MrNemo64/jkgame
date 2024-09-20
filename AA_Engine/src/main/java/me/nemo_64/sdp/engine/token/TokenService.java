package me.nemo_64.sdp.engine.token;

import me.nemo_64.sdp.engine.Main;
import me.nemo_64.sdp.utilities.Result;
import me.nemo_64.sdp.utilities.player.LoadedPlayer;
import me.nemo_64.sdp.utilities.player.PlayerManager;
import me.nemo_64.sdp.utilities.socket.ServerSocketSession;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.logging.Logger;

public class TokenService {

    private static final String UNKNOWN_USER_CODE = "UNKNOWN_USER";
    private static final String INCORRECT_PASSWORD = "INCORRECT_PASSWORD";
    private static final String NOT_SERVING_TOKENS = "NOT_SERVING_TOKENS";
    private static final Logger LOGGER = Logger.getLogger(TokenService.class.getName());

    static {
        LOGGER.setParent(Main.ENGINE_LOGGER);
    }

    public enum TokenServiceCreationError {
        COULD_NOT_CREATE_SERVER,
    }

    public static Result<TokenService, TokenServiceCreationError> createService(int port, int attempts, int timeOut,
            PlayerManager playerManager) {
        try {
            ServerSocket server = new ServerSocket(port);
            Random r = new Random();
            return Result.ok(new TokenService(Executors.newSingleThreadExecutor(),
                    server,
                    playerManager,
                    timeOut,
                    (s) -> s.getInetAddress().getHostAddress() + ":" + s.getPort(),
                    (socket) -> attempts,
                    (uuid) -> String.valueOf(r.nextInt())));
        } catch (IOException e) {
            return Result.err(TokenServiceCreationError.COULD_NOT_CREATE_SERVER);
        }
    }

    private final ExecutorService clientHandlers;
    private final ServerSocket server;
    private final PlayerManager playerManager;
    private final int timeOut;
    private final Function<Socket, String> addressFormatter;
    private final ToIntFunction<Socket> socketAttemptsGenerator;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread listeningThread;
    private final AtomicReference<Optional<GameTokenCreationRequest>> actualRequest = new AtomicReference<>(
            Optional.empty());
    private final Function<UUID, String> tokenGenerator;

    public TokenService(ExecutorService clientHandlers, ServerSocket server, PlayerManager playerManager, int timeOut,
            Function<Socket, String> addressFormatter, ToIntFunction<Socket> socketAttemptsGenerator,
            Function<UUID, String> tokenGenerator) {
        this.clientHandlers = clientHandlers;
        this.server = server;
        this.playerManager = playerManager;
        this.timeOut = timeOut;
        this.addressFormatter = addressFormatter;
        this.socketAttemptsGenerator = socketAttemptsGenerator;
        this.tokenGenerator = tokenGenerator;
    }

    private void listenForConnection() {
        while (running.get()) {
            try {
                Socket socket = server.accept();
                socket.setSoTimeout(timeOut);
                LOGGER.info("Client connected on %s with a read time out of %d"
                        .formatted(addressFormatter.apply(socket), timeOut));
                handleClient(socket);
            } catch (SocketException | SocketTimeoutException e) {
                // IGNORED
            } catch (IOException e) {
                LOGGER.warning("An IO exception occurred while accepting a client: %s".formatted(e.getMessage()));
            }
        }
    }

    private void handleClient(Socket socket) {
        clientHandlers.submit(() -> {
            String socketAddress = addressFormatter.apply(socket);
            var sessionResult = ServerSocketSession.createSession(socket, socketAttemptsGenerator.applyAsInt(socket),
                    LOGGER, socketAddress);
            if (sessionResult.isError()) {
                LOGGER.warning("Could not create session for " + socketAddress + ". Connection will be ignored.");
                return;
            }
            var session = sessionResult.value();
            if (session.enquire().isPresent())
                return;

            if (actualRequest.get().isEmpty()) {
                session.sendString(NOT_SERVING_TOKENS);
                session.endSession();
                return;
            }

            if (actualRequest.get().get().remainingTokensToCreate() == 0) {
                session.sendString(NOT_SERVING_TOKENS);
                session.endSession();
                finishRequest();
                return;
            }

            String[] credentials;
            while (true) {
                var receivedUserCredentials = session.readString();
                if (receivedUserCredentials.isError())
                    return;
                credentials = receivedUserCredentials.value().split(":");
                if (credentials.length == 2)
                    break;
                if (session.sendNack().isPresent())
                    return;
                if (session.decrementAttemptsAndGet() <= 0) {
                    LOGGER.info(socketAddress + " ran out of attempts while sending credentials.");
                    session.endSession();
                    return;
                }
            }
            var player = playerManager.getPlayer(credentials[0]);
            if (player.isEmpty()) {
                if (session.sendString(UNKNOWN_USER_CODE).isPresent())
                    return;
                session.endSession();
                return;
            }
            if (!player.get().getPassword().equals(credentials[1])) {
                if (session.sendString(INCORRECT_PASSWORD).isPresent())
                    return;
                session.endSession();
                return;
            }
            GameTokenCreationRequest request = actualRequest.get().get();
            Optional<String> token = request.createToken(player.get().getId());
            if (token.isEmpty())
                LOGGER.warning("Tried to create a token for a non existing player. This should not have happened");
            session.sendString(token.map((t) -> request.password + ":" + t).orElse(UNKNOWN_USER_CODE));
            session.endSession();
        });
    }

    private void finishRequest() {
        if (actualRequest.get().isEmpty())
            return;
        GameTokenCreationRequest request = actualRequest.getAndSet(Optional.empty()).get();
        request.future.complete(request.tokens);
        request.getRequester().onRequestCompleted(request.getTokens());
        LOGGER.info("Finished the creation of " + request.getCreatedTokens());
    }

    public boolean isRunning() {
        return running.get();
    }

    public Optional<GameTokenCreationRequest> requestTokensCreation(int amount, GameTokenCreationRequester requester) {
        if (actualRequest.get().isPresent()) {
            LOGGER.info("A request to create " + amount + " tokens was made, but a request is already ongoing");
            return Optional.empty();
        }
        LOGGER.info("A request to create " + amount + " tokens was made");
        actualRequest
                .set(Optional.of(new GameTokenCreationRequest(requester.password().serialize(), amount, requester)));
        return actualRequest.get();
    }

    public boolean stop() {
        if (!isRunning() || listeningThread == null)
            return false;
        LOGGER.info("Stopped a token service on %d".formatted(server.getLocalPort()));
        listeningThread.interrupt();
        running.set(false);
        return true;
    }

    public boolean start() {
        if (isRunning() || listeningThread != null)
            return false;
        LOGGER.info("Started a token service on %d".formatted(server.getLocalPort()));
        listeningThread = new Thread(this::listenForConnection);
        listeningThread.start();
        running.set(true);
        return true;
    }

    public class GameTokenCreationRequest {

        private final String password;
        private final int tokensToCreate;
        private final GameTokenCreationRequester requester;
        private final Map<String, LoadedPlayer> tokens = new ConcurrentHashMap<>();
        private final CompletableFuture<Map<String, LoadedPlayer>> future = new CompletableFuture<>();

        public GameTokenCreationRequest(String password, int tokensToCreate, GameTokenCreationRequester requester) {
            this.password = password;
            this.tokensToCreate = tokensToCreate;
            this.requester = requester;
        }

        public Optional<String> createToken(UUID uuid) {
            String token = tokenGenerator.apply(uuid);
            Optional<LoadedPlayer> player = TokenService.this.playerManager.getPlayer(uuid);
            if (player.isEmpty())
                return Optional.empty();
            tokens.put(token, player.get());
            if (remainingTokensToCreate() == 0)
                finishRequest();
            requester.onTokenCreated(token, player.get());
            return Optional.of(token);
        }

        public int getTokensToCreate() {
            return tokensToCreate;
        }

        public int remainingTokensToCreate() {
            return getTokensToCreate() - getCreatedTokens();
        }

        public void finishRequest() {
            LOGGER.info("Finishing the creation of %d request early. %d were created.".formatted(getTokensToCreate(),
                    getCreatedTokens()));
            TokenService.this.finishRequest();
        }

        public CompletableFuture<Map<String, LoadedPlayer>> getFuture() {
            return future;
        }

        public boolean didFinish() {
            return future.isDone();
        }

        public GameTokenCreationRequester getRequester() {
            return requester;
        }

        public Map<String, LoadedPlayer> getTokens() {
            return Collections.unmodifiableMap(tokens);
        }

        public int getCreatedTokens() {
            return tokens.size();
        }
    }

}
