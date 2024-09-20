package me.nemo_64.sdp.utilities.player;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import me.nemo_64.sdp.utilities.player.PlayerUtil;

import java.io.Reader;
import java.io.StringReader;

public class TestPlayerUtil {

    private static final String TEST_CHARS = """
            a
            b
            c
            d
            e
            f
            g
            h
            i
            j
            k
            l
            m
            n
            o
            p
            q
            r
            s
            t
            u
            v
            w
            x
            y
            z
            _
            0
            1
            2
            3
            4
            5
            6
            7
            8
            9""";

    private static Reader createStream() {
        return new StringReader(TEST_CHARS);
    }

    @Test
    public void testDefaultCharactersWithValidString() {
        PlayerUtil util = PlayerUtil.setUp(null);
        String valid = "Nemo_64";
        Assertions.assertTrue(util.isValidAlias(valid));
    }

    @Test
    public void testDefaultCharactersWithInvalidString() {
        PlayerUtil util = PlayerUtil.setUp(null);
        String invalid = "+DestucTHOR+";
        Assertions.assertFalse(util.isValidAlias(invalid));
    }

    @Test
    public void testCharactersWithValidString() {
        PlayerUtil util = PlayerUtil.setUp(createStream());
        String valid = "nemo_64";
        Assertions.assertTrue(util.isValidAlias(valid));
    }

    @Test
    public void testCharactersWithInvalidString() {
        PlayerUtil util = PlayerUtil.setUp(createStream());
        String invalid = "Nemo_64";
        Assertions.assertFalse(util.isValidAlias(invalid));
    }

}
