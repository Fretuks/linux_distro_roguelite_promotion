package dev.frederik.promotion;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class RegistrationEndToEndTest {
    @Inject
    DataSource dataSource;

    @Test
    void registeredPlayerCanLoginAndAccessPrivateGameDetails() throws Exception {
        // Given: the isolated test database has seeded platforms and a new player wants Windows access.
        String suffix = Long.toString(System.nanoTime());
        String username = "e2e_" + suffix;
        String email = username + "@example.com";
        Integer windowsPlatformId = firstPlatformId();

        // When: the player completes the public workflow through the API.
        Integer userId = given()
                .contentType("application/json")
                .body(Map.of(
                        "username", username,
                        "email", email,
                        "newsletter_optin", false))
                .when()
                .post("/users")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        Integer registrationId = given()
                .contentType("application/json")
                .body(Map.of(
                        "user_id", userId,
                        "platform_id", windowsPlatformId))
                .when()
                .post("/registrations")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        String token = given()
                .contentType("application/json")
                .body(Map.of(
                        "username", username,
                        "email", email))
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("access_token");

        Map<String, Object> details = given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/auth/game-details")
                .then()
                .statusCode(200)
                .extract()
                .as(Map.class);

        List<Map<String, Object>> userRegistrations = given()
                .when()
                .get("/users/{id}/registrations", userId)
                .then()
                .statusCode(200)
                .extract()
                .as(List.class);

        // Then: the API returns protected game intel and the registration exists in the database.
        assertNotNull(registrationId);
        assertEquals("Authenticated build notes", details.get("title"));
        assertEquals(1, userRegistrations.size());
        assertEquals("Windows", userRegistrations.get(0).get("platform_name"));
        assertTrue(registrationExistsInDatabase(userId, windowsPlatformId));
    }

    private Integer firstPlatformId() {
        return given()
                .when()
                .get("/platforms")
                .then()
                .statusCode(200)
                .extract()
                .path("[0].id");
    }

    private boolean registrationExistsInDatabase(int userId, int platformId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM registrations
                     WHERE user_id = ? AND platform_id = ?
                     """)) {
            statement.setInt(1, userId);
            statement.setInt(2, platformId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) == 1;
            }
        }
    }
}
