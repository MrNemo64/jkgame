package me.nemo_64.sdp.utilities.configuration;

import java.util.Optional;
import java.util.function.Predicate;

record ConfigurationKeyToParse<T>(Class<T> type, Predicate<T> checker, boolean optional, T def) {

    public Optional<ParsedConfigurationKey<T>> parse(Object value) {
        if (value == null)
            return Optional.empty();
        if (type != value.getClass())
            return Optional.empty();
        return checker.test(type.cast(value)) ? Optional.of(new ParsedConfigurationKey<>(type, type.cast(value)))
                : Optional.empty();
    }

}