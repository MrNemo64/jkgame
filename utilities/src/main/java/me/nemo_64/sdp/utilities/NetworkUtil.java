package me.nemo_64.sdp.utilities;

import me.nemo_64.sdp.utilities.configuration.ConfigurationParseError;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NetworkUtil {

    public static int ATTEMPTS = 3;

    private NetworkUtil() {
    }

    public static final Pattern PORT_PATTERN = Pattern.compile("\\d{1,5}");

    public static final String addressOf(InetSocketAddress address) {
        return new StringBuilder(address.getHostString()).append(':').append(address.getPort()).toString();
    }

    public static boolean isValidPort(String port) {
        return PORT_PATTERN.matcher(port).matches();
    }

    public static boolean isValidPort(int port) {
        return 0 < port && port <= 65535;
    }

    public static Optional<Integer> parsePort(String port) {
        return NumberUtil.tryParseInt(port).filter(NetworkUtil::isValidPort);
    }

    public static int calculateLrc(String str) {
        int actualLrc = 0;
        for (char c : str.toCharArray()) {
            actualLrc ^= c;
        }
        return actualLrc;
    }

    public enum PrimitiveError {
        TIME_OUT,
        GENERIC_IO
    }

    public enum ComplexError {
        TIME_OUT,
        OUT_OF_ATTEMPTS,
        GENERIC_IO,
        RECEIVED_EOT;

        public static ComplexError fromPrimitiveError(PrimitiveError err) {
            return switch (err) {
                case TIME_OUT -> ComplexError.TIME_OUT;
                case GENERIC_IO -> ComplexError.GENERIC_IO;
            };
        }
    }

    public static Result<Character, PrimitiveError> readCharacter(DataInputStream in) {
        try {
            return Result.ok(in.readChar());
        } catch (SocketTimeoutException e) {
            return Result.err(PrimitiveError.TIME_OUT);
        } catch (IOException e) {
            return Result.err(PrimitiveError.GENERIC_IO);
        }
    }

    public static Result<Integer, PrimitiveError> readInt(DataInputStream in) {
        try {
            return Result.ok(in.readInt());
        } catch (SocketTimeoutException e) {
            return Result.err(PrimitiveError.TIME_OUT);
        } catch (IOException e) {
            return Result.err(PrimitiveError.GENERIC_IO);
        }
    }

    public static Result<String, ComplexError> readString(DataInputStream in,
            DataOutputStream out) {
        return NetworkUtil.readString(in, out, NetworkUtil.ATTEMPTS);
    }

    public static Result<String, ComplexError> readString(DataInputStream in,
            DataOutputStream out,
            int attempts) {
        return NetworkUtil.readString(in, out, IntReference.from(attempts));
    }

    public static Result<String, ComplexError> readString(DataInputStream in,
            DataOutputStream out,
            IntReference attempts) {
        while (true) {
            // Search start of string
            while (true) {
                var readChar = NetworkUtil.readCharacter(in);
                if (readChar.isError())
                    return Result.err(ComplexError.fromPrimitiveError(readChar.error()));
                if (readChar.value() == AsciiTable.STX)
                    break;
                if (readChar.value() == AsciiTable.EOT)
                    return Result.err(ComplexError.RECEIVED_EOT);
                NetworkUtil.sendCharacter(out, AsciiTable.NACK);
                if (attempts.decrementAndGet() <= 0)
                    return Result.err(ComplexError.OUT_OF_ATTEMPTS);
            }

            // Read all text
            StringBuilder accumulator = new StringBuilder();
            while (true) {
                var readChar = NetworkUtil.readCharacter(in);
                if (readChar.isError())
                    return Result.err(ComplexError.fromPrimitiveError(readChar.error()));
                if (readChar.value() == AsciiTable.ETX)
                    break;
                accumulator.append(readChar.value());
            }
            String readString = accumulator.toString();

            // Read checksum
            var readChecksum = NetworkUtil.readInt(in);
            if (readChecksum.isError())
                return Result.err(ComplexError.fromPrimitiveError(readChecksum.error()));
            int expectedChecksum = readChecksum.value();
            int obtainedChecksum = NetworkUtil.calculateLrc(readString);
            if (expectedChecksum == obtainedChecksum) {
                NetworkUtil.sendCharacter(out, AsciiTable.ACK);
                return Result.ok(readString);
            }
            NetworkUtil.sendCharacter(out, AsciiTable.NACK);
            if (attempts.decrementAndGet() == 0)
                return Result.err(ComplexError.OUT_OF_ATTEMPTS);
        }
    }

    public static Optional<PrimitiveError> sendInt(DataOutputStream out,
            int value) {
        try {
            out.writeInt(value);
            return Optional.empty();
        } catch (SocketTimeoutException e) {
            return Optional.of(PrimitiveError.TIME_OUT);
        } catch (IOException e) {
            return Optional.of(PrimitiveError.GENERIC_IO);
        }
    }

    public static Optional<PrimitiveError> sendCharacter(DataOutputStream out,
            char character) {
        try {
            out.writeChar(character);
            return Optional.empty();
        } catch (SocketTimeoutException e) {
            return Optional.of(PrimitiveError.TIME_OUT);
        } catch (IOException e) {
            return Optional.of(PrimitiveError.GENERIC_IO);
        }
    }

    public static Optional<ComplexError> sendString(DataInputStream in,
            DataOutputStream out,
            String str) {
        return NetworkUtil.sendString(in, out, str, NetworkUtil.ATTEMPTS);
    }

    public static Optional<ComplexError> sendString(DataInputStream in,
            DataOutputStream out,
            String str,
            int attempts) {
        return NetworkUtil.sendString(in, out, str, IntReference.from(attempts));
    }

    public static Optional<ComplexError> sendString(DataInputStream in,
            DataOutputStream out,
            String str,
            IntReference attempts) {
        try {
            while (true) {
                out.writeChar(AsciiTable.STX);
                for (char c : str.toCharArray())
                    out.writeChar(c);
                out.writeChar(AsciiTable.ETX);
                out.writeInt(NetworkUtil.calculateLrc(str));
                var readChar = NetworkUtil.readCharacter(in);
                if (readChar.isError())
                    return Optional.of(ComplexError.fromPrimitiveError(readChar.error()));
                if (readChar.value() == AsciiTable.ACK)
                    return Optional.empty();
                if (attempts.decrementAndGet() <= 0)
                    return Optional.of(ComplexError.OUT_OF_ATTEMPTS);
            }
        } catch (IOException e) {
            return Optional.of(ComplexError.GENERIC_IO);
        }
    }

    public static Result<?, ConfigurationParseError> httpVersionParser(String s) {
        if (s == null || s.isEmpty())
            return Result.err(ConfigurationParseError.invalidValue("Empty string"));
        try {
            return Result.ok(HttpClient.Version.valueOf(s));
        } catch (IllegalArgumentException e) {
            String options = Arrays.stream(HttpClient.Version.values())
                    .map(Enum::name)
                    .collect(Collectors.joining("', '", "'", "'"));
            return Result.err(ConfigurationParseError
                    .invalidValue(s + " is not a valid HTTP version. Valid values are: " + options));
        }
    }

}
