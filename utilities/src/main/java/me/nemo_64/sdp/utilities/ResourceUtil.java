package me.nemo_64.sdp.utilities;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Random;
import java.util.stream.IntStream;

public class ResourceUtil {

    private static final char[] VALID = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final Random RANDOM = new Random();

    public static InputStream getResource(String name) {
        return ResourceUtil.class.getResourceAsStream(name);
    }

    public static byte[] getBytes(String resource) {
        try (var in = getResource(resource)) {
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (;;) {
                int readAmount = in.read(buffer);
                if (readAmount <= 0) {
                    break;
                }
                byteArray.write(buffer, 0, readAmount);
            }
            return byteArray.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String randomString(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i <= length; i++) {
            builder.append(VALID[RANDOM.nextInt(VALID.length)]);
        }
        return builder.toString();
    }

}
