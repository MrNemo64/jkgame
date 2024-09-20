package me.nemo_64.sdp.utilities.configuration;

public record ParsedConfigurationKey<T>(Class<T> type, T value) {
}
