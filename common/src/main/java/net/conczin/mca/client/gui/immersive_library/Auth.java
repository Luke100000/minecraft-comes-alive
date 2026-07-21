package net.conczin.mca.client.gui.immersive_library;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.immersive_library.responses.Response;
import net.conczin.mca.client.gui.immersive_library.responses.SuccessResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

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

    public static void logout() {
        if (hasToken()) {
            Api.request(Api.HttpMethod.DELETE, SuccessResponse.class, "v2/auth/token");
        }
        clearToken();
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static AuthenticationRequest authenticate(String username) {
        currentToken = newToken();
        Response response = Api.request(
                Api.HttpMethod.POST,
                AuthenticationRequest.class,
                "v2/auth/start",
                Map.of(),
                Map.of("username", username, "token_hash", sha256(currentToken))
        );
        if (response instanceof AuthenticationRequest request) {
            return request;
        }
        clearToken();
        return null;
    }

    public record AuthenticationRequest(String login_url, String verification_code, int expires_in) implements Response {
        public String loginUrl() {
            String baseUrl = Config.getInstance().immersiveLibraryUrl.replaceAll("/+$", "");
            return login_url.startsWith("/") ? baseUrl + login_url : login_url;
        }

        public String verificationCode() {
            return verification_code;
        }
    }
}
