package me.nemo_64.sdp.utilities.secure;

import com.google.gson.JsonObject;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Optional;

// https://mkyong.com/java/java-symmetric-key-cryptography-example/
public class SymmetricCipher {

    private static final byte[] SALT = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 };

    private static SecretKeySpec createKey(String password) {
        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(password.toCharArray(), SALT, 65536, 256); // AES-256
            SecretKeySpec keySpec = new SecretKeySpec(f.generateSecret(spec).getEncoded(), "AES");
            return keySpec;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    public static SymmetricCipher create(String secret) throws NoSuchPaddingException, NoSuchAlgorithmException {
        SecretKeySpec secretKey = createKey(secret);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        return new SymmetricCipher(secret, "AES/CBC/PKCS5Padding", secretKey, cipher);
    }

    public static Optional<SymmetricCipher> deserialize(JsonObject json) {
        try {
            String secret = json.get("secret").getAsString();
            return Optional.of(create(secret));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private final String secret;
    private final String algorithm;
    private final SecretKeySpec secretKey;
    private final Cipher cipher;

    public SymmetricCipher(String secret, String algorithm, SecretKeySpec secretKey, Cipher cipher) {
        this.secret = secret;
        this.algorithm = algorithm;
        this.secretKey = secretKey;
        this.cipher = cipher;
    }

    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("secret", secret);
        return json;
    }

    public byte[] encrypt(byte[] bytes) {
        try {
            this.cipher.init(Cipher.ENCRYPT_MODE, this.secretKey, new IvParameterSpec(SALT));
            return this.cipher.doFinal(bytes);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (IllegalBlockSizeException e) {
            throw new RuntimeException(e);
        } catch (BadPaddingException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] decrypt(byte[] bytes) {
        try {
            this.cipher.init(Cipher.DECRYPT_MODE, this.secretKey, new IvParameterSpec(SALT));
            return this.cipher.doFinal(bytes);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (IllegalBlockSizeException e) {
            throw new RuntimeException(e);
        } catch (BadPaddingException e) {
            System.out.println("INVALID PASSWORD");
            System.exit(-1);
            return null;
        } catch (InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }
    }

}
