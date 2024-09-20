package me.nemo_64.sdp.utilities.configuration;

import me.nemo_64.sdp.utilities.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;

public class ConfigurationBuilder {

    public static final Function<String, Result<?, ConfigurationParseError>> INTEGER_PARSER = (str) -> {
        try {
            return Result.ok(Integer.parseInt(str));
        } catch (NumberFormatException e) {
            return Result.err(ConfigurationParseError.invalidFormat(str + " is not a valid integer"));
        }
    };

    public static final Function<String, Result<?, ConfigurationParseError>> DOUBLE_PARSER = (str) -> {
        try {
            return Result.ok(Double.parseDouble(str));
        } catch (NumberFormatException e) {
            return Result.err(ConfigurationParseError.invalidFormat(str + " is not a valid double"));
        }
    };

    public static final Function<String, Result<?, ConfigurationParseError>> STRING_PARSER = Result::ok;

    public static final Function<String, Result<?, ConfigurationParseError>> BOOLEAN_PARSER = (str) -> {
        try {
            return Result.ok(Boolean.parseBoolean(str));
        } catch (NumberFormatException e) {
            return Result.err(ConfigurationParseError.invalidFormat(str + " is not a valid boolean"));
        }
    };

    public static ConfigurationBuilder newBuilder() {
        return new ConfigurationBuilder();
    }

    public static Optional<Properties> loadProperties(Path file) {
        if (file == null)
            return Optional.empty();
        if (!Files.exists(file) || !Files.isRegularFile(file))
            return Optional.empty();
        try (var input = Files.newInputStream(file)) {
            Properties properties = new Properties();
            properties.load(input);
            return Optional.of(properties);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private final Map<Class<?>, Function<String, Result<?, ConfigurationParseError>>> parsers = new HashMap<>();
    private final Map<String, ConfigurationKeyToParse<?>> entries = new HashMap<>();
    private Logger LOGGER = Logger.getLogger(ConfigurationBuilder.class.getName());
    private Path file = null;

    private ConfigurationBuilder() {
    }

    public ConfigurationBuilder registerParser(Class<?> type,
            Function<String, Result<?, ConfigurationParseError>> parser) {
        parsers.put(Objects.requireNonNull(type), Objects.requireNonNull(parser));
        return this;
    }

    public ConfigurationBuilder registerPrimitiveParsers() {
        registerParser(Integer.class, INTEGER_PARSER);
        registerParser(Double.class, DOUBLE_PARSER);
        registerParser(String.class, STRING_PARSER);
        registerParser(Boolean.class, BOOLEAN_PARSER);
        return this;
    }

    public ConfigurationBuilder register(String key, Class<?> type) {
        return register(key, type, true, null, (a) -> true);
    }

    public ConfigurationBuilder withLogger(Logger logger) {
        this.LOGGER = Objects.requireNonNull(logger);
        return this;
    }

    public ConfigurationBuilder register(String key, Class<?> type, boolean optional) {
        return register(key, type, optional, null, (a) -> true);
    }

    public <T> ConfigurationBuilder register(String key, Class<T> type, boolean optional, Predicate<T> checker) {
        return register(key, type, optional, null, checker);
    }

    public <T> ConfigurationBuilder register(String key, Class<T> type, T def) {
        return register(key, type, false, def);
    }

    public <T> ConfigurationBuilder register(String key, Class<T> type, boolean optional, T def) {
        return register(key, type, optional, def, (value) -> true);
    }

    public <T> ConfigurationBuilder register(String key, Class<T> type, boolean optional, T def, Predicate<T> checker) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(type);
        Objects.requireNonNull(checker);
        entries.put(key, new ConfigurationKeyToParse<>(type, checker, optional, def));
        return this;
    }

    public ConfigurationBuilder withFile(Path file) {
        this.file = Objects.requireNonNull(file);
        return this;
    }

    public Result<Config, ConfigurationParseError> create() {
        return loadProperties(file).map(this::createFromProperties).orElseGet(this::tryCreateDefaultConfiguration);
    }

    private Result<Config, ConfigurationParseError> createFromProperties(Properties properties) {
        LOGGER.info("Parsing configuration");
        Map<String, ParsedConfigurationKey<?>> values = new HashMap<>();
        for (var entry : entries.entrySet()) {
            String key = entry.getKey();
            ConfigurationKeyToParse<?> val = entry.getValue();
            if (!parsers.containsKey(val.type())) {
                LOGGER.warning("The configuration does not know how to parse a string to a " + val.type().getName()
                        + " for the key " + key);
                return Result.err(
                        ConfigurationParseError.error("Unknown type " + val.type().getName() + " for the key " + key));
            }
            if (!properties.containsKey(key)) {
                Object def = val.def();
                if (def == null && !val.optional()) {
                    LOGGER.warning("Missing required entry of the configuration: " + key);
                    return Result.err(ConfigurationParseError.error("Missing key:" + key));
                }
                var parsedVal = val.parse(def);
                if (parsedVal.isPresent()) {
                    LOGGER.info("Configuration file does not have key '%s', using the default value: '%s'"
                            .formatted(key, parsedVal.get()));
                    values.put(key, parsedVal.get());
                } else {
                    LOGGER.info(
                            "Configuration file does not have key '%s' but the default value (%s) is invalid. Since its optional it will be taken as not preset"
                                    .formatted(key, def));
                }
                continue;
            }
            var valueInConf = parsers.get(val.type()).apply(properties.getProperty(key));
            if (valueInConf.isError()) {
                LOGGER.warning("Cannot parse value for key %s: %s".formatted(key, valueInConf.error()));
                return Result.err(ConfigurationParseError
                        .error("Could not parse key %s: %s".formatted(key, valueInConf.error())));
            }
            var parsedKey = val.parse(valueInConf.value());
            if (parsedKey.isEmpty()) {
                LOGGER.warning("The key " + key + " does not met the required criteria");
                return Result.err(ConfigurationParseError
                        .invalidValue(valueInConf.value() + " does not met the required criteria"));
            }
            values.put(key, parsedKey.get());
        }
        return Result.ok(new Config(values));
    }

    private Result<Config, ConfigurationParseError> tryCreateDefaultConfiguration() {
        LOGGER.info("Trying to create the default config");
        Map<String, ParsedConfigurationKey<?>> values = new HashMap<>();
        for (var entry : entries.entrySet()) {
            Object def = entry.getValue().def();
            if (def == null && !entry.getValue().optional()) {
                LOGGER.warning("Could not recreate the key " + entry.getKey());
                return Result.err(ConfigurationParseError.error("Missing key: " + entry.getKey()));
            }
            if (def != null) {
                var val = entry.getValue().parse(def);
                if (val.isPresent()) {
                    values.put(entry.getKey(), val.get());
                } else if (!entry.getValue().optional()) {
                    LOGGER.warning(
                            "Missing key: '%s' and default value '%s' is invalid".formatted(entry.getKey(), def));
                    return Result.err(ConfigurationParseError.error(
                            "Missing key: '%s' and default value '%s' is invalid".formatted(entry.getKey(), def)));
                }
            }
        }
        return Result.ok(new Config(values));
    }

}
