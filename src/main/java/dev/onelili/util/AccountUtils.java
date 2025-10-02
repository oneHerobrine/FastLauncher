package dev.onelili.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.onelili.Main;
import fr.litarvan.openauth.AuthPoints;
import fr.litarvan.openauth.Authenticator;
import fr.litarvan.openauth.model.AuthAgent;
import fr.litarvan.openauth.model.response.AuthResponse;
import lombok.NonNull;
import lombok.SneakyThrows;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.Objects;

public class AccountUtils {
    @SneakyThrows
    public static AuthData generate() {
        OkHttpClient client = new OkHttpClient();
        @SuppressWarnings("deprecation")
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                "{\"server\": \"" + Main.getMeta().getString("server-id") + "\"}"
        );
        Request request = new Request.Builder()
                .url(Main.getMeta().getString("uni-auth-url") + "fastregister/generateaccount")
                .post(body)
                .build();
        JsonObject json;
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code: " + response);
            }
            json = JsonParser.parseString(Objects.requireNonNull(response.body()).string()).getAsJsonObject();
        }
        Objects.requireNonNull(json);
        String name = json.get("account").getAsString();
        String password = json.get("password").getAsString();
        return AuthData
                .builder()
                .username(name)
                .password(password)
                .build();
    }

    @SneakyThrows
    public static AuthData register(@NonNull String username, @NonNull String password) {
        OkHttpClient client = new OkHttpClient();
        JsonObject req = new JsonObject();
        req.addProperty("server", Main.getMeta().getString("server-id"));
        req.addProperty("username", username);
        req.addProperty("password", password);
        @SuppressWarnings("deprecation")
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                new Gson().toJson(req)
        );
        Request request = new Request.Builder()
                .url(Main.getMeta().getString("uni-auth-url") + "fastregister/registeraccount")
                .post(body)
                .build();
        JsonObject json;
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code: " + response);
            }
            json = JsonParser.parseString(Objects.requireNonNull(response.body()).string()).getAsJsonObject();
        }
        Objects.requireNonNull(json);
        String retName = json.get("account").getAsString();
        String retPassword = json.get("retPassword").getAsString();
        return AuthData
                .builder()
                .username(retName)
                .password(retPassword)
                .build();
    }

    @SneakyThrows
    public static void setLoginWith(@NonNull String username, @NonNull String password) {
        OkHttpClient client = new OkHttpClient();
        JsonObject req = new JsonObject();
        req.addProperty("username", username);
        req.addProperty("password", password);
        @SuppressWarnings("deprecation")
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                new Gson().toJson(req)
        );
        Request request = new Request.Builder()
                .url(Main.getMeta().getString("uni-auth-url") + "fastregister/loginwithuniauth")
                .post(body)
                .build();
        JsonObject json;
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code: " + response);
            }
            json = JsonParser.parseString(Objects.requireNonNull(response.body()).string()).getAsJsonObject();
        }
        Objects.requireNonNull(json);
    }

    @SneakyThrows
    public static @NonNull AuthResponse login(@NonNull AuthData authData) {
        Authenticator authenticator = new Authenticator(Main.getMeta().getString("uni-auth-url") + "yggdrasil/authserver/", AuthPoints.NORMAL_AUTH_POINTS);
        AuthAgent agent = new AuthAgent("UniAuthReloaded", 1);
        return authenticator.authenticate(agent, authData.getUsername(), authData.getPassword(), "88888888-8888-8888-8888-888888888888");
    }
}
