package me.nemo_64.sdp.registry.util;

public final class ConfigurationEntry {

    public static final String SOCKET_PORT = "socket-port";
    public static final String HTTPS_PORT = "https-port";
    public static final String HTTPS_IP = "https-ip";
    public static final String HTTPS_SSL_PROTOCOL = "https-ssl-protocol";
    public static final String KEY_STORE_TYPE = "key-store-type";
    public static final String KEY_FACTORY_ALGORITHM = "key-factory-algorithm";
    public static final String TRUST_FACTORY_ALGORITHM = "trust-factory-algorithm";
    public static final String PLAYERS_FOLDER = "players-folder";
    public static final String ALLOWED_CHARACTERS_FILE = "allowed-characters-file";
    public static final String ATTEMPTS_PER_REQUEST = "attempts-per-request";
    public static final String TIME_OUT = "time-out";
    public static final String CERTIFICATE_FILE_PASSWORD = "https-certificate-file-password";
    public static final String CERTIFICATE_FILE = "https-certificate-file";
    public static final String REQUEST_BODY_INVALID = "request-body-invalid";
    public static final String PLAYER_NOT_SPECIFIED_RESPONSE = "player-not-specified-message";
    public static final String PLAYER_NOT_FOUND_RESPONSE = "player-not-found-message";
    public static final String ALIAS_NOT_AVAILABLE_RESPONSE = "alias-not-available-response";
    public static final String PLAYER_CREATE_ERROR_RESPONSE = "player-create-error-response";
    public static final String POST_MISSING_FILED_RESPONSE = "post-missing-field-response";
    public static final String INCORRECT_PASSWORD_POST_RESPONSE = "incorrect-password-post-response";

    private ConfigurationEntry() {
    }

}
