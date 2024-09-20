package me.nemo_64.sdp.utilities.configuration;

public sealed interface ConfigurationParseError {

    static InvalidFormat invalidFormat(String message) {
        return new InvalidFormat(message);
    }

    static InvalidValue invalidValue(String message) {
        return new InvalidValue(message);
    }

    static ConfigurationParseError error(String message) {
        return new AbstractConfigurationParseError(message);
    }

    String message();

    sealed class AbstractConfigurationParseError implements ConfigurationParseError {
        private final String message;

        public AbstractConfigurationParseError(String message) {
            this.message = message;
        }

        @Override
        public String message() {
            return message;
        }
    }

    final class InvalidValue extends AbstractConfigurationParseError {
        public InvalidValue(String message) {
            super(message);
        }
    }

    final class InvalidFormat extends AbstractConfigurationParseError {
        public InvalidFormat(String message) {
            super(message);
        }
    }

}
