package me.nemo_64.sdp.registry.rest;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import me.nemo_64.sdp.registry.Main;
import me.nemo_64.sdp.registry.util.ConfigurationEntry;
import me.nemo_64.sdp.utilities.configuration.Config;
import me.nemo_64.sdp.utilities.player.PlayerManager;
import me.nemo_64.sdp.utilities.secure.SSLFix;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class PlayerHttpsServer {

    static final Logger LOGGER = Logger.getLogger(PlayerHttpsServer.class.getName());

    static {
        try {
            Path file = Paths.get("log/AA_Registry.https.log");
            if (!Files.exists(file)) {
                Path parent = file.getParent();
                if (!Files.exists(parent))
                    Files.createDirectories(parent);
                Files.createFile(file);
            }
            FileHandler handler = new FileHandler(file.toString(), true);
            handler.setFormatter(new Formatter() {
                @Override
                public String format(LogRecord record) {
                    return new StringBuilder(record.getLevel().getName())
                            .append(' ')
                            .append(time(record.getInstant()))
                            .append(" (")
                            .append(record.getSourceClassName())
                            .append("#")
                            .append(record.getSourceMethodName())
                            .append("): ")
                            .append(record.getMessage())
                            .append('\n').toString();
                }

                private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
                        .ofPattern("yyyy/MM/dd HH:mm:ss");

                private static String time(Instant instant) {
                    return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(DATE_TIME_FORMATTER);
                }
            });
            LOGGER.setParent(Main.REGISTRY_LOGGER);
            LOGGER.addHandler(handler);
        } catch (IOException e) {
            LOGGER.warning("Could not set up the log file: " + e.getMessage());
        }
    }

    private HttpsServer server;
    // private HttpServer server;

    public PlayerHttpsServer(PlayerManager playerManager) {
        SSLFix.execute();
        // https://www.delftstack.com/howto/java/java-https-server/
        InetSocketAddress address = new InetSocketAddress(Config.getString(ConfigurationEntry.HTTPS_IP),
                Config.getInt(ConfigurationEntry.HTTPS_PORT));
        try {
            this.server = HttpsServer.create(address, 0);
            // this.server = HttpServer.create(address, 0);
        } catch (IOException e) {
            LOGGER.warning("Could not create https server: " + e.getMessage());
            System.exit(-1);
            return;
        }
        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance(Config.getString(ConfigurationEntry.HTTPS_SSL_PROTOCOL));
        } catch (NoSuchAlgorithmException e) {
            LOGGER.warning("Could not find the ssl protocol '%s': %s"
                    .formatted(Config.getString(ConfigurationEntry.HTTPS_SSL_PROTOCOL), e.getMessage()));
            System.exit(-1);
            return;
        }
        String certificatePassword = Config.getString(ConfigurationEntry.CERTIFICATE_FILE_PASSWORD);
        KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance(Config.getString(ConfigurationEntry.KEY_STORE_TYPE));
        } catch (KeyStoreException e) {
            LOGGER.warning("Could not find the key store type '%s': %s"
                    .formatted(Config.getString(ConfigurationEntry.KEY_STORE_TYPE), e.getMessage()));
            System.exit(-1);
            return;
        }
        FileInputStream certificateStream;
        try {
            certificateStream = new FileInputStream(Config.getString(ConfigurationEntry.CERTIFICATE_FILE));
        } catch (FileNotFoundException e) {
            LOGGER.warning("Could not find the file with the certificate: " + e.getMessage());
            System.exit(-1);
            return;
        }
        try {
            keyStore.load(certificateStream, certificatePassword.toCharArray());
        } catch (IOException e) {
            if (e.getCause() instanceof UnrecoverableKeyException unrecoverableKey) {
                LOGGER.warning("Invalid certificate password! '%s' is not the password: %s"
                        .formatted(certificatePassword, unrecoverableKey.getMessage()));
            } else {
                LOGGER.warning("IO exception while reading the certificate: " + e.getMessage());
            }
            System.exit(-1);
            return;
        } catch (NoSuchAlgorithmException e) {
            LOGGER.warning(
                    "The algorithm used to check the integrity of the keystore cannot be found: " + e.getMessage());
            System.exit(-1);
            return;
        } catch (CertificateException e) {
            LOGGER.warning("Could not load the certificate: " + e.getMessage());
            System.exit(-1);
            return;
        }
        KeyManagerFactory keyManagerFactory;
        try {
            keyManagerFactory = KeyManagerFactory
                    .getInstance(Config.getString(ConfigurationEntry.KEY_FACTORY_ALGORITHM));
        } catch (NoSuchAlgorithmException e) {
            LOGGER.warning("The algorithm '%s' used for the key factory cannot be found: %s"
                    .formatted(Config.getString(ConfigurationEntry.KEY_FACTORY_ALGORITHM), e.getMessage()));
            System.exit(-1);
            return;
        }
        try {
            keyManagerFactory.init(keyStore, certificatePassword.toCharArray());
        } catch (KeyStoreException e) {
            LOGGER.warning("Could not initialize the key manager factory: " + e.getMessage());
            System.exit(-1);
            return;
        } catch (NoSuchAlgorithmException e) {
            LOGGER.warning("Could not initialize the key manager factory because the algorithm '%s' cannot be found: %s"
                    .formatted(Config.getString(ConfigurationEntry.KEY_FACTORY_ALGORITHM), e.getMessage()));
        } catch (UnrecoverableKeyException e) {
            LOGGER.warning("Could not initialize the key manager factory because the password '%s' is not correct: %s"
                    .formatted(certificatePassword, e.getMessage()));
        }

        TrustManagerFactory trustManagerFactory;
        try {
            trustManagerFactory = TrustManagerFactory
                    .getInstance(Config.getString(ConfigurationEntry.TRUST_FACTORY_ALGORITHM));
        } catch (NoSuchAlgorithmException e) {
            LOGGER.warning("The algorithm '%s' used for the trust factory cannot be found: %s"
                    .formatted(Config.getString(ConfigurationEntry.TRUST_FACTORY_ALGORITHM), e.getMessage()));
            System.exit(-1);
            return;
        }
        try {
            trustManagerFactory.init(keyStore);
        } catch (KeyStoreException e) {
            LOGGER.warning("Could not initialize the trust manager factory: " + e.getMessage());
            System.exit(-1);
            return;
        }

        try {
            sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
        } catch (KeyManagementException e) {
            LOGGER.warning("Could not initialize ssl: " + e.getMessage());
            System.exit(-1);
            return;
        }
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
            @Override
            public void configure(HttpsParameters params) {
                String client = params.getClientAddress().getHostString() + ":" + params.getClientAddress().getPort();
                try {
                    LOGGER.info(client + " is connecting");
                    SSLContext context = getSSLContext();
                    SSLEngine engine = context.createSSLEngine();
                    params.setNeedClientAuth(false);
                    params.setCipherSuites(engine.getEnabledCipherSuites());
                    params.setProtocols(engine.getEnabledProtocols());

                    params.setSSLParameters(context.getSupportedSSLParameters());
                    LOGGER.info("Accepted " + client);
                } catch (Exception e) {
                    LOGGER.warning("Could not accept %s: %s".formatted(client, e.getMessage()));
                }
            }
        });

        server.setExecutor(Main.REGISTRY_EXECUTORS);
        server.createContext("/player", new PlayerRequestHandler(playerManager));
        server.start();
        LOGGER.info("Started REST on " + server.getAddress().getHostName() + ":" + server.getAddress().getPort());
    }

}
