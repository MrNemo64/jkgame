package me.nemo_64.sdp.player.util;

import java.util.Scanner;
import java.util.function.Consumer;

public class ConsoleUtil {

    public static final Scanner IN = new Scanner(System.in);

    private ConsoleUtil() {
    }

    public static int readInt(Consumer<String> onNan) {
        while (true) {
            String read = IN.nextLine();
            try {
                return Integer.parseInt(read);
            } catch (NumberFormatException e) {
                onNan.accept(read);
            }
        }
    }

}
