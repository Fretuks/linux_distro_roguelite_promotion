package dev.frederik.promotion;

import com.fasterxml.jackson.databind.JsonNode;
import io.smallrye.jwt.build.Jwt;
import jakarta.annotation.security.RolesAllowed;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {
    private static final String ISSUER = "https://kernel-panic.local/issuer";

    private final DatabaseService database;
    private final String adminUsername;
    private final String adminPassword;
    private final JsonWebToken jwt;

    @Inject
    public AuthResource(
            DatabaseService database,
            @ConfigProperty(name = "auth.admin.username") String adminUsername,
            @ConfigProperty(name = "auth.admin.password") String adminPassword,
            JsonWebToken jwt) {
        this.database = database;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.jwt = jwt;
    }

    @POST
    @PermitAll
    @Path("/login")
    public Response login(JsonNode body) {
        String username = readText(body, "username", "username is required.");
        String password = readOptionalText(body, "password");
        if (password != null && secureEquals(username, adminUsername) && secureEquals(password, adminPassword)) {
            return Response.ok(tokenResponse(username, Set.of("User", "Admin"), null)).build();
        }

        String email = readText(body, "email", "email is required.");
        Map<String, Object> user = database.findOne(
                "SELECT id, username, email FROM users WHERE username = ? AND email = ?",
                username,
                email);
        if (user == null) {
            throw new ApiException(401, "Invalid username or email.");
        }

        return Response.ok(tokenResponse(username, Set.of("User"), ((Number) user.get("id")).longValue())).build();
    }

    @GET
    @Path("/me")
    @RolesAllowed({"User", "Admin"})
    public Map<String, Object> me() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("username", jwt.getName());
        response.put("groups", jwt.getGroups());
        response.put("user_id", jwt.getClaim("user_id"));
        return response;
    }

    @GET
    @Path("/game-details")
    @RolesAllowed({"User", "Admin"})
    public Map<String, Object> gameDetails() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("title", "Authenticated build notes");
        response.put("unlocked_after_registration", true);
        response.put("available_starting_distros", Set.of("Ubuntu", "Fedora", "Mint"));
        response.put("starting_languages", Set.of("Python", "JavaScript", "Rust", "Java"));
        response.put("hidden_enemies", Set.of("Telemetry Collector", "DRM Guardian", "Blue Screen", "Walled Garden"));
        response.put("economy", Map.of(
                "Bandwidth", "earned through play and used for pulls",
                "Entropy", "banner pull currency",
                "Bits", "run-only shop currency",
                "Compute Credits", "premium currency for pulls and cosmetics"));
        response.put("developer_note", "Registered players can preview this intel before launch.");
        return response;
    }

    private Map<String, Object> tokenResponse(String username, Set<String> groups, Long userId) {
        var builder = Jwt.upn(username)
                .issuer(ISSUER)
                .groups(groups)
                .expiresIn(Duration.ofHours(2));
        if (userId != null) {
            builder.claim("user_id", userId);
        }
        String token = builder.sign();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("token_type", "Bearer");
        response.put("access_token", token);
        response.put("expires_in", 7200);
        response.put("groups", groups);
        response.put("user_id", userId);
        return response;
    }

    private String readText(JsonNode body, String field, String message) {
        if (body == null || body.isNull() || !body.has(field) || body.get(field).isNull()) {
            throw new ApiException(400, message);
        }
        if (!body.get(field).isTextual() || body.get(field).asText().isBlank()) {
            throw new ApiException(400, message);
        }
        return body.get(field).asText().trim();
    }

    private String readOptionalText(JsonNode body, String field) {
        if (body == null || body.isNull() || !body.has(field) || body.get(field).isNull()) {
            return null;
        }
        if (!body.get(field).isTextual() || body.get(field).asText().isBlank()) {
            return null;
        }
        return body.get(field).asText().trim();
    }

    private boolean secureEquals(String first, String second) {
        return MessageDigest.isEqual(
                first.getBytes(StandardCharsets.UTF_8),
                second.getBytes(StandardCharsets.UTF_8));
    }
}
