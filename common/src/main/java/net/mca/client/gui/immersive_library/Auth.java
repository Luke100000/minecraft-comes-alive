package net.mca.client.gui.immersive_library;

import com.google.gson.JsonObject;
import net.mca.Config;
import net.mca.MCA;
import net.minecraft.util.Util;

import javax.annotation.Nullable;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class Auth {
    static final SecureRandom random = new SecureRandom();

    static String currentToken;

    private static String newToken() {
        byte[] bytes = new byte[64];
        random.nextBytes(bytes);
        return sha256(new String(bytes));
    }

    @Nullable
    public static String loadToken() {
        try {
            return Files.readString(Paths.get("./immersiveLibraryToken"));
        } catch (IOException e) {
            return null;
        }
    }

    @Nullable
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
            Files.writeString(Paths.get("./immersiveLibraryToken"), currentToken);
        } catch (IOException e) {
            MCA.LOGGER.error(e);
        }
    }

    public static void clearToken() {
        //noinspection ResultOfMethodCallIgnored
        Paths.get("./immersiveLibraryToken").toFile().delete();
    }

    private static void write(String path, String content) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path))) {
            writer.write(content);
        }
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String createDataState(String username, String token) {
        JsonObject json = new JsonObject();
        json.addProperty("username", Base64.getEncoder().encodeToString(username.getBytes()));
        json.addProperty("token", Base64.getEncoder().encodeToString(sha256(token).getBytes()));
        return json.toString();
    }

    public static void authenticate(String username) {
        try {
            String tmpdir = Files.createTempDirectory("immersive_library").toFile().getAbsolutePath();

            // The unique, private token used to authenticate once authorized
            currentToken = newToken();

            // Store new token
            saveToken();

            // Inject token into request
            String content = RES_PAGE;
            content = content.replace("{URL}", Config.getInstance().immersiveLibraryUrl);
            content = content.replace("{STATE}", createDataState(username, currentToken));
            write(tmpdir + "/page.html", content);

            // Open the authorization URL in the user's default web browser
            Util.getOperatingSystem().open((new File(tmpdir + "/page.html")).toURI());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final String RES_PAGE = """
            <html lang="en">
            <head>
                <title>Login</title>
                <script src="https://accounts.google.com/gsi/client" async defer></script>
                 <style>
                    .container {
                        display: flex;
                        flex-flow: wrap;
                        justify-content: center;
                        align-items: center;
                        text-align: center;
                        min-height: 95vh;
                    }
            
                    .container hr {
                        width: 100%;
                    }
            
                    body {
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: 100vh;
                        background: radial-gradient(ellipse at bottom, #0d1d31 0%, #0c0d13 100%);
                        overflow: hidden;
                    }
            
                    .chunk {
                        color: black;
                        font-family: Calibri, sans-serif;
                        background-color: white;
                        opacity: 95%;
                        border-radius: 32px;
                        padding: 16px 32px 32px 32px;
                        box-shadow: 4px 6px 64px;
                    }
            </style>
            </head>
            <body class="background">
            <div class="container">
                <div class="chunk">
                    <h1>Authenticate</h1>
                    Immersive Library uses your Google account as authentication.
                    <br/>
                    Only your Google user id is stored.
                    <br/> <br/>
            
                    <div id="g_id_onload"
                         data-client_id="854276437682-lkb8uqt14lrt5ctcbknaia4s3j429kme.apps.googleusercontent.com"
                         data-login_uri="{URL}/v1/auth"
                         data-ux_mode="redirect"
                         data-auto_prompt="false"
                         data-state='{STATE}'>
                    </div>
            
                    <div class="g_id_signin"
                         data-type="standard"
                         data-size="large"
                         data-theme="filled_black"
                         data-text="sign_in_with"
                         data-shape="rectangular"
                         data-logo_alignment="left">
                    </div>
                </div>
            </div>
            </body>
            </html>
            """;
}