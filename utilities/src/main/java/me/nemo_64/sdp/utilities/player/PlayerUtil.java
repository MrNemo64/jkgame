package me.nemo_64.sdp.utilities.player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

public class PlayerUtil {

    private static final Logger LOGGER = Logger.getLogger(PlayerUtil.class.getName());

    public static void setLoggerParent(Logger logger) {
        LOGGER.setParent(logger);
    }

    private final Set<Character> validCharacters;

    private PlayerUtil(Set<Character> validCharacters) {
        this.validCharacters = validCharacters;
    }

    public static PlayerUtil setUp() {
        return PlayerUtil.setUp(null);
    }

    public static PlayerUtil setUp(Reader allowedCharacters) {
        if (allowedCharacters == null) {
            LOGGER.info("Using the default allowed characters list");
            return new PlayerUtil(Set.of(PlayerUtil.defaultAllowedCharacters()));
        }
        Set<Character> valid = new TreeSet<>();
        Character[] allowed = null;
        try (BufferedReader reader = new BufferedReader(allowedCharacters)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.length() != 1) {
                    LOGGER.warning("Invalid character at line %d of the allowed characters file: '%s'"
                            .formatted(lineNumber, line));
                } else {
                    char c = line.charAt(0); // length == 1
                    if (!valid.add(c))
                        LOGGER.info("The character %c is duplicated on the list (line %d)".formatted(c, lineNumber));
                }
                lineNumber++;
            }
            allowed = valid.toArray(Character[]::new);
        } catch (IOException e) {
            LOGGER.info("Could not load the %s file with the allowed characters. Using the default allowed list: %s"
                    .formatted(allowedCharacters.toString(), e.getMessage()));
            allowed = PlayerUtil.defaultAllowedCharacters();
        }
        return new PlayerUtil(Set.of(allowed));
    }

    public boolean isValidAlias(String alias) {
        if (alias == null || alias.isBlank())
            return false;
        for (char c : alias.toCharArray())
            if (!validCharacters.contains(c))
                return false;
        return true;
    }

    public static Character[] defaultAllowedCharacters() {
        return new Character[] {
                'a',
                'b',
                'c',
                'd',
                'e',
                'f',
                'g',
                'h',
                'i',
                'j',
                'k',
                'l',
                'm',
                'n',
                'o',
                'p',
                'q',
                'r',
                's',
                't',
                'u',
                'v',
                'w',
                'x',
                'y',
                'z',
                'A',
                'B',
                'C',
                'D',
                'E',
                'F',
                'G',
                'H',
                'I',
                'J',
                'K',
                'L',
                'M',
                'N',
                'O',
                'P',
                'Q',
                'R',
                'S',
                'T',
                'U',
                'V',
                'W',
                'X',
                'Y',
                '_',
                '0',
                '1',
                '2',
                '3',
                '4',
                '5',
                '6',
                '7',
                '8',
                '9' };
    }

}
