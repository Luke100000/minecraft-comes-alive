package net.conczin.mca.client.gui.immersive_library;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.immersive_library.responses.ErrorResponse;
import net.conczin.mca.client.gui.immersive_library.responses.Response;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class Api {
    private static final Gson gson = new Gson();
    private static final int TIMEOUT_MS = 10_000;

    public static Response request(HttpMethod httpMethod, Class<? extends Response> expectedAnswer, String url) {
        return request(httpMethod, expectedAnswer, url, null, null);
    }

    public static Response request(HttpMethod httpMethod, Class<? extends Response> expectedAnswer, String url, Map<String, String> queryParams) {
        return request(httpMethod, expectedAnswer, url, queryParams, null);
    }

    public static Response request(HttpMethod httpMethod, Class<? extends Response> expectedAnswer, String url, Map<String, String> queryParams, Map<String, ?> body) {
        try {
            String fullUrl = Config.getInstance().immersiveLibraryUrl + "/" + url;

            // Append query params
            if (queryParams != null) {
                fullUrl = queryParams.keySet().stream()
                        .map(key -> key + "=" + URLEncoder.encode(queryParams.get(key), StandardCharsets.UTF_8))
                        .collect(Collectors.joining("&", fullUrl + "?", ""));
            }

            HttpURLConnection con = (HttpURLConnection) (URI.create(fullUrl)).toURL().openConnection();
            con.setConnectTimeout(TIMEOUT_MS);
            con.setReadTimeout(TIMEOUT_MS);

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
                String jsonBody = gson.toJson(body);
                con.getOutputStream().write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            // Send the request and read response
            int responseCode = con.getResponseCode();
            if (responseCode >= 400) {
                String error = con.getErrorStream() == null ? "" : IOUtils.toString(con.getErrorStream(), StandardCharsets.UTF_8);
                JsonObject object = gson.fromJson(error, JsonObject.class);
                boolean hasMessage = object != null && object.has("message");
                return new ErrorResponse(responseCode, hasMessage ? object.get("message").getAsString() : error);
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
            MCA.LOGGER.warn(e);
            return new ErrorResponse(-1, e.toString());
        } catch (Exception e) {
            MCA.LOGGER.error(e);
            return new ErrorResponse(-1, e.toString());
        }
    }

    public enum HttpMethod {
        POST, GET, DELETE, PUT
    }
}
