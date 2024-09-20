package me.nemo_64.sdp.utilities.configuration;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public class Config {

    private static final Function<String, RuntimeException> THROWABLE_SUPPLIER = (key) -> new IllegalStateException(
            "Tried to retrieve " + key + " but there is no entry for it in the config");

    private static Config INSTANCE;

    public static Config getInstance() {
        return INSTANCE;
    }

    public static void setInstance(Config instance) {
        Config.INSTANCE = Objects.requireNonNull(instance);
    }

    public static Optional<Integer> getOptionalInt(String key) {
        return getOptional(key, Integer.class);
    }

    public static Optional<Double> getOptionalDouble(String key) {
        return getOptional(key, Double.class);
    }

    public static Optional<Boolean> getOptionalBoolean(String key) {
        return getOptional(key, Boolean.class);
    }

    public static Optional<String> getOptionalString(String key) {
        return getOptional(key, String.class);
    }

    public static int getInt(String key) {
        return get(key, Integer.class);
    }

    public static double getDouble(String key) {
        return get(key, Double.class);
    }

    public static boolean getBoolean(String key) {
        return get(key, Boolean.class);
    }

    public static String getString(String key) {
        return get(key, String.class);
    }

    public static Optional<Path> getOptionalPath(String key) {
        return getOptionalString(key).map(Path::of);
    }

    public static Path getPath(String key) {
        return getOptionalPath(key).orElseThrow(() -> THROWABLE_SUPPLIER.apply(key));
    }

    public static <T> boolean has(String key, Class<T> type) {
        return getOptional(key, type).isPresent();
    }

    public static <T> T get(String key, Class<T> type) {
        return getOptional(key, type).orElseThrow(() -> THROWABLE_SUPPLIER.apply(key));
    }

    public static <T> Optional<T> getOptional(String key, Class<T> type) {
        if (INSTANCE == null)
            return Optional.empty();
        if (!INSTANCE.values.containsKey(key))
            return Optional.empty();
        if (INSTANCE.values.get(key).type() != type)
            return Optional.empty();
        return Optional.of(type.cast(INSTANCE.values.get(key).value()));
    }

    private final Map<String, ParsedConfigurationKey<?>> values;

    public Config(Map<String, ParsedConfigurationKey<?>> values) {
        this.values = Objects.requireNonNull(values);
    }

    public String display(String entryPrefix) {
        StringBuilder str = new StringBuilder();
        for (var entry : values.entrySet()) {
            String key = entry.getKey();
            ParsedConfigurationKey<?> value = entry.getValue();
            str.append('\n')
                    .append(entryPrefix)
                    .append(key)
                    .append(" (")
                    .append(value.type().getName())
                    .append("): ")
                    .append(value.value());
        }
        return str.toString();
    }

}
