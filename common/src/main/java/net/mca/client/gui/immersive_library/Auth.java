package net.mca.client.gui.immersive_library;

import com.google.gson.JsonObject;
import net.mca.Config;
import net.mca.MCA;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public class Auth {
    private static final SecureRandom RANDOM = new SecureRandom();

    private static String currentToken;

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String loadToken() {
        try {
            return Files.readString(Paths.get("./immersiveLibraryToken_v2"));
        } catch (IOException e) {
            return null;
        }
    }

    public static String getToken() {
        if (currentToken == null) {
            currentToken = loadToken();
        }
        return currentToken;
    }

    public static boolean hasToken() {
        return getToken() != null;
    }

    public static void saveToken() {
        try {
            Files.writeString(Paths.get("./immersiveLibraryToken_v2"), currentToken);
        } catch (IOException e) {
            MCA.LOGGER.error(e);
        }
    }

    public static void clearToken() {
        currentToken = null;
        //noinspection ResultOfMethodCallIgnored
        Paths.get("./immersiveLibraryToken_v2").toFile().delete();
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String createDataState(String username, String token) {
        JsonObject json = new JsonObject();
        json.addProperty("username", Base64.getEncoder().encodeToString(username.getBytes(StandardCharsets.UTF_8)));
        json.addProperty("token", Base64.getEncoder().encodeToString(sha256(token).getBytes(StandardCharsets.UTF_8)));
        return Base64.getEncoder().encodeToString(json.toString().getBytes(StandardCharsets.UTF_8));
    }

    public static void authenticate(String username) {
        currentToken = newToken();
        String url = Config.getInstance().immersiveLibraryUrl + "/v1/login?state=" + createDataState(username, currentToken);
        Util.getOperatingSystem().open(url);
    }
}
