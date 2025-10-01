package dev.onelili.util;

import com.google.gson.JsonObject;
import fr.litarvan.openauth.model.response.AuthResponse;
import lombok.*;


@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Getter
public class AuthData {
    private final String username;
    private final String password;
    @Setter
    private AuthResponse response;

    public static @NonNull AuthData deserialize(@NonNull JsonObject json) {
        return AuthData
                .builder()
                .username(json.get("username").getAsString())
                .password(json.get("password").getAsString())
                .build();
    }

    public static @NonNull JsonObject serialize(@NonNull AuthData data) {
        JsonObject json = new JsonObject();
        json.addProperty("username", data.getUsername());
        json.addProperty("password",  data.getPassword());
        return json;
    }
}
