package me.nemo_64.sdp.utilities;

import java.util.Optional;

public class NumberUtil {

    private NumberUtil() {
    }

    public static Optional<Integer> tryParseInt(String str) {
        try {
            return Optional.of(Integer.parseInt(str));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public static boolean isGraterThanZero(int i) {
        return i > 0;
    }

}
