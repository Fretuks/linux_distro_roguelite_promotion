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
class PromotionIntegrationTest {
    @Inject
    DataSource dataSource;

    @Test
    void platformsEndpointReturnsCurrentLaunchTargetsFromIsolatedDatabase() throws Exception {
        // Given: the Quarkus app started with the isolated test database and seeded launch platforms.

        // When: the API client requests available platforms.
        List<String> platformNames = given()
                .when()
                .get("/platforms")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("name");

        // Then: the API and database expose only the current launch targets.
        assertEquals(List.of("Windows", "Linux", "Mobile (Android)"), platformNames);
        assertEquals(3, countCurrentPlatformsInDatabase());
    }

    @Test
    void creatingUserThroughApiPersistsUserInDatabase() throws Exception {
        // Given: a unique pre-registration payload.
        String suffix = Long.toString(System.nanoTime());
        String username = "integration_" + suffix;
        String email = username + "@example.com";
        Map<String, Object> payload = Map.of(
                "username", username,
                "email", email,
                "newsletter_optin", true);

        // When: the user is created through the REST API.
        Integer userId = given()
                .contentType("application/json")
                .body(payload)
                .when()
                .post("/users")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // Then: the returned id exists and the row is stored in the isolated database.
        assertNotNull(userId);
        Map<String, Object> row = findUserInDatabase(username);
        assertEquals(email, row.get("email"));
        assertEquals(Boolean.TRUE, row.get("newsletter_optin"));
    }

    private int countCurrentPlatformsInDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM platforms
                     WHERE name IN ('Windows', 'Linux', 'Mobile (Android)')
                     """);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private Map<String, Object> findUserInDatabase(String username) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT email, newsletter_optin
                     FROM users
                     WHERE username = ?
                     """)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return Map.of(
                        "email", resultSet.getString("email"),
                        "newsletter_optin", resultSet.getBoolean("newsletter_optin"));
            }
        }
    }
}
