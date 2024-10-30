package net.mca.client.gui.immersive_library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.mca.Config;
import net.mca.MCA;
import net.mca.client.gui.immersive_library.responses.*;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class Api {
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapterFactory(new RecordTypeAdapterFactory())
            .create();

    public enum HttpMethod {
        POST, GET, DELETE, PUT
    }

    public static Response request(HttpMethod httpMethod, Class<? extends Response> expectedAnswer, String url) {
        return request(httpMethod, expectedAnswer, url, null, null);
    }

    public static Response request(HttpMethod httpMethod, Class<? extends Response> expectedAnswer, String url, Map<String, String> queryParams) {
        return request(httpMethod, expectedAnswer, url, queryParams, null);
    }

    public static Response request(HttpMethod httpMethod, Class<? extends Response> expectedAnswer, String url, Map<String, String> queryParams, Map<String, String> body) {
        try {
            String fullUrl = Config.getServerConfig().immersiveLibraryUrl + (url.contains("v2") ? "/" : "/v1/") + url;

            // Append query params
            if (queryParams != null) {
                fullUrl = queryParams.keySet().stream()
                        .map(key -> key + "=" + URLEncoder.encode(queryParams.get(key), StandardCharsets.UTF_8))
                        .collect(Collectors.joining("&", fullUrl + "?", ""));
            }

            HttpURLConnection con = (HttpURLConnection) (new URL(fullUrl)).openConnection();

            // Set request method
            con.setRequestMethod(httpMethod.name());

            // Set request headers
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Accept-Encoding", "gzip");
            con.setRequestProperty("Accept", "application/json");

            // Authenticate
            if (Auth.hasToken()) {
                con.setRequestProperty("Authorization", "Bearer " + Auth.getToken());
            }

            // Set request body
            if (body != null && !body.isEmpty()) {
                con.setDoOutput(true);
                Gson gson = new Gson();
                String jsonBody = gson.toJson(body);
                con.getOutputStream().write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            // Send the request and read response
            if (con.getErrorStream() != null) {
                int responseCode = con.getResponseCode();
                String error = IOUtils.toString(con.getErrorStream(), StandardCharsets.UTF_8);
                JsonObject object = gson.fromJson(error, JsonObject.class);
                return new ErrorResponse(responseCode, object.get("message").getAsString());
            }

            // Parse answer
            String response;
            if ("gzip".equals(con.getContentEncoding())) {
                response = IOUtils.toString(new GZIPInputStream(con.getInputStream()), StandardCharsets.UTF_8);
            } else {
                response = IOUtils.toString(con.getInputStream(), StandardCharsets.UTF_8);
            }

            return gson.fromJson(response, expectedAnswer);
        } catch (IOException e) {
            return new ErrorResponse(-1, e.toString());
        } catch (Exception e) {
            MCA.LOGGER.error(e);
            return new ErrorResponse(-1, e.toString());
        }
    }
}
