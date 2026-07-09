package dev.frederik.promotion;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PromotionResource {
    private final DatabaseService database;
    private final RegistrationEmailService registrationEmailService;

    @Inject
    public PromotionResource(DatabaseService database, RegistrationEmailService registrationEmailService) {
        this.database = database;
        this.registrationEmailService = registrationEmailService;
    }

    @GET
    @Path("/api/health")
    public Map<String, Object> health() {
        return Map.of("message", "Pre-registration API is running.");
    }

    @POST
    @Path("/users")
    public Response createUser(JsonNode body) {
        String username = Validation.requiredText(body, "username", "username is required.");
        String email = Validation.requiredText(body, "email", "A valid email is required.");
        Validation.requireEmail(email);
        boolean newsletterOptin = Validation.optionalBoolean(body, "newsletter_optin", false);

        long id;
        try {
            id = database.insertAndReturnId(
                    "INSERT INTO users (username, email, newsletter_optin) VALUES (?, ?, ?)",
                    username, email, newsletterOptin);
        } catch (RuntimeException exception) {
            throw mapConflict(exception, "username or email already exists.");
        }

        try {
            registrationEmailService.sendRegistrationMail(id, username, email);
        } catch (RuntimeException exception) {
            database.execute("DELETE FROM users WHERE id = ?", id);
            throw new ApiException(502, "Registration email could not be sent. Please try again later.");
        }

        return Response.status(Response.Status.CREATED).entity(userById(id)).build();
    }

    @GET
    @Path("/users/{id}")
    public Map<String, Object> getUser(@PathParam("id") String rawId) {
        long id = Validation.parseId(rawId, "Invalid user id.");
        Map<String, Object> user = userByIdOrNull(id);
        if (user == null) {
            throw new ApiException(404, "User not found.");
        }
        return user;
    }

    @PUT
    @Path("/users/{id}")
    public Map<String, Object> updateUser(@PathParam("id") String rawId, JsonNode body) {
        long id = Validation.parseId(rawId, "Invalid user id.");
        requireExists("SELECT id FROM users WHERE id = ?", id, "User not found.");

        List<String> updates = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (body.has("username")) {
            String username = Validation.requiredText(body, "username", "username must not be empty.");
            updates.add("username = ?");
            params.add(username);
        }
        if (body.has("email")) {
            String email = Validation.requiredText(body, "email", "A valid email is required.");
            Validation.requireEmail(email);
            updates.add("email = ?");
            params.add(email);
        }
        if (body.has("newsletter_optin")) {
            updates.add("newsletter_optin = ?");
            params.add(Validation.optionalBoolean(body, "newsletter_optin", false));
        }
        requireUpdates(updates);
        params.add(id);

        try {
            database.execute("UPDATE users SET " + String.join(", ", updates) + " WHERE id = ?", params.toArray());
        } catch (RuntimeException exception) {
            throw mapConflict(exception, "username or email already exists.");
        }
        return userById(id);
    }

    @DELETE
    @Path("/users/{id}")
    public Map<String, Object> deleteUser(@PathParam("id") String rawId) {
        long id = Validation.parseId(rawId, "Invalid user id.");
        if (database.execute("DELETE FROM users WHERE id = ?", id) == 0) {
            throw new ApiException(404, "User not found.");
        }
        return Map.of("message", "User deleted.");
    }

    @GET
    @Path("/users/{id}/registrations")
    public List<Map<String, Object>> userRegistrations(@PathParam("id") String rawId) {
        long id = Validation.parseId(rawId, "Invalid user id.");
        requireExists("SELECT id FROM users WHERE id = ?", id, "User not found.");
        return database.query("""
                SELECT registrations.id, registrations.user_id, registrations.platform_id,
                       platforms.name AS platform_name, registrations.created_at
                FROM registrations
                JOIN platforms ON platforms.id = registrations.platform_id
                WHERE registrations.user_id = ?
                ORDER BY registrations.id
                """, id);
    }

    @GET
    @Path("/platforms")
    public List<Map<String, Object>> platforms() {
        return database.query("""
                SELECT *
                FROM platforms
                WHERE name IN ('Windows', 'Linux', 'Mobile (Android)')
                ORDER BY CASE name
                  WHEN 'Windows' THEN 1
                  WHEN 'Linux' THEN 2
                  WHEN 'Mobile (Android)' THEN 3
                  ELSE 4
                END
                """);
    }

    @GET
    @Path("/platforms/{id}")
    public Map<String, Object> platform(@PathParam("id") String rawId) {
        long id = Validation.parseId(rawId, "Invalid platform id.");
        Map<String, Object> platform = database.findOne("SELECT * FROM platforms WHERE id = ?", id);
        if (platform == null) {
            throw new ApiException(404, "Platform not found.");
        }
        return platform;
    }

    @POST
    @Path("/registrations")
    public Response createRegistration(JsonNode body) {
        long userId = Validation.requiredPositiveLong(body, "user_id", "user_id is required.");
        long platformId = Validation.requiredPositiveLong(body, "platform_id", "platform_id is required.");
        requireExists("SELECT id FROM users WHERE id = ?", userId, "User does not exist.", 400);
        requireExists("SELECT id FROM platforms WHERE id = ?", platformId, "Platform does not exist.", 400);

        long id;
        try {
            id = database.insertAndReturnId(
                    "INSERT INTO registrations (user_id, platform_id) VALUES (?, ?)",
                    userId, platformId);
        } catch (RuntimeException exception) {
            throw mapConflict(exception, "User is already registered for this platform.");
        }

        database.updateReachedMilestones();
        return Response.status(Response.Status.CREATED)
                .entity(database.findOne("SELECT * FROM registrations WHERE id = ?", id))
                .build();
    }

    @GET
    @Path("/registrations/count")
    public Map<String, Object> registrationCount() {
        return Map.of("count", database.registrationCount());
    }

    @DELETE
    @Path("/registrations/{id}")
    public Map<String, Object> deleteRegistration(@PathParam("id") String rawId) {
        long id = Validation.parseId(rawId, "Invalid registration id.");
        if (database.execute("DELETE FROM registrations WHERE id = ?", id) == 0) {
            throw new ApiException(404, "Registration not found.");
        }
        return Map.of("message", "Registration deleted.");
    }

    @GET
    @Path("/milestones")
    public List<Map<String, Object>> milestones() {
        long count = database.registrationCount();
        return database.query("SELECT * FROM milestones ORDER BY target_count, id").stream()
                .map(row -> milestoneResponse(row, count))
                .toList();
    }

    @GET
    @Path("/milestones/{id}/rewards")
    public List<Map<String, Object>> milestoneRewards(@PathParam("id") String rawId) {
        long id = Validation.parseId(rawId, "Invalid milestone id.");
        requireExists("SELECT id FROM milestones WHERE id = ?", id, "Milestone not found.");
        return database.query("SELECT * FROM rewards WHERE milestone_id = ? ORDER BY id", id);
    }

    @POST
    @Path("/milestones")
    @RolesAllowed("Admin")
    public Response createMilestone(JsonNode body) {
        String title = Validation.requiredText(body, "title", "title is required.");
        long targetCount = Validation.requiredPositiveLong(body, "target_count", "target_count must be a positive integer.");
        long currentCount = database.registrationCount();
        long id = database.insertAndReturnId(
                "INSERT INTO milestones (title, target_count, reached) VALUES (?, ?, ?)",
                title, targetCount, currentCount >= targetCount);
        return Response.status(Response.Status.CREATED)
                .entity(milestoneResponse(database.findOne("SELECT * FROM milestones WHERE id = ?", id), currentCount))
                .build();
    }

    @PUT
    @Path("/milestones/{id}")
    @RolesAllowed("Admin")
    public Map<String, Object> updateMilestone(
            @PathParam("id") String rawId,
            JsonNode body) {
        long id = Validation.parseId(rawId, "Invalid milestone id.");
        requireExists("SELECT id FROM milestones WHERE id = ?", id, "Milestone not found.");

        List<String> updates = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (body.has("title")) {
            updates.add("title = ?");
            params.add(Validation.requiredText(body, "title", "title must not be empty."));
        }
        if (body.has("target_count")) {
            updates.add("target_count = ?");
            params.add(Validation.requiredPositiveLong(body, "target_count", "target_count must be a positive integer."));
        }
        if (body.has("reached")) {
            updates.add("reached = ?");
            params.add(Validation.optionalBoolean(body, "reached", false));
        }
        requireUpdates(updates);
        params.add(id);
        database.execute("UPDATE milestones SET " + String.join(", ", updates) + " WHERE id = ?", params.toArray());

        long currentCount = database.registrationCount();
        return milestoneResponse(database.findOne("SELECT * FROM milestones WHERE id = ?", id), currentCount);
    }

    @DELETE
    @Path("/milestones/{id}")
    @RolesAllowed("Admin")
    public Map<String, Object> deleteMilestone(
            @PathParam("id") String rawId) {
        long id = Validation.parseId(rawId, "Invalid milestone id.");
        if (database.execute("DELETE FROM milestones WHERE id = ?", id) == 0) {
            throw new ApiException(404, "Milestone not found.");
        }
        return Map.of("message", "Milestone deleted.");
    }

    @GET
    @Path("/rewards")
    public List<Map<String, Object>> rewards() {
        return database.query("SELECT * FROM rewards ORDER BY id");
    }

    @GET
    @Path("/rewards/milestone/{milestoneId}")
    public List<Map<String, Object>> rewardsByMilestone(@PathParam("milestoneId") String rawId) {
        long milestoneId = Validation.parseId(rawId, "Invalid milestone id.");
        requireExists("SELECT id FROM milestones WHERE id = ?", milestoneId, "Milestone not found.");
        return database.query("SELECT * FROM rewards WHERE milestone_id = ? ORDER BY id", milestoneId);
    }

    @POST
    @Path("/rewards")
    @RolesAllowed("Admin")
    public Response createReward(JsonNode body) {
        String name = Validation.requiredText(body, "name", "name is required.");
        String description = Validation.optionalText(body, "description");
        String imageUrl = Validation.optionalText(body, "image_url");
        long milestoneId = Validation.requiredPositiveLong(body, "milestone_id", "milestone_id must be a positive integer.");
        requireExists("SELECT id FROM milestones WHERE id = ?", milestoneId, "Milestone does not exist.", 400);

        long id;
        try {
            id = database.insertAndReturnId(
                    "INSERT INTO rewards (milestone_id, name, description, image_url) VALUES (?, ?, ?, ?)",
                    milestoneId, name, description, imageUrl);
        } catch (RuntimeException exception) {
            throw mapConflict(exception, "Reward conflict.");
        }
        return Response.status(Response.Status.CREATED)
                .entity(database.findOne("SELECT * FROM rewards WHERE id = ?", id))
                .build();
    }

    @PUT
    @Path("/rewards/{id}")
    @RolesAllowed("Admin")
    public Map<String, Object> updateReward(
            @PathParam("id") String rawId,
            JsonNode body) {
        long id = Validation.parseId(rawId, "Invalid reward id.");
        requireExists("SELECT id FROM rewards WHERE id = ?", id, "Reward not found.");

        List<String> updates = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (body.has("name")) {
            updates.add("name = ?");
            params.add(Validation.requiredText(body, "name", "name must not be empty."));
        }
        if (body.has("description")) {
            updates.add("description = ?");
            params.add(Validation.optionalText(body, "description"));
        }
        if (body.has("image_url")) {
            updates.add("image_url = ?");
            params.add(Validation.optionalText(body, "image_url"));
        }
        if (body.has("milestone_id")) {
            long milestoneId = Validation.requiredPositiveLong(body, "milestone_id", "milestone_id must be a positive integer.");
            requireExists("SELECT id FROM milestones WHERE id = ?", milestoneId, "Milestone does not exist.", 400);
            updates.add("milestone_id = ?");
            params.add(milestoneId);
        }
        requireUpdates(updates);
        params.add(id);
        database.execute("UPDATE rewards SET " + String.join(", ", updates) + " WHERE id = ?", params.toArray());
        return database.findOne("SELECT * FROM rewards WHERE id = ?", id);
    }

    @DELETE
    @Path("/rewards/{id}")
    @RolesAllowed("Admin")
    public Map<String, Object> deleteReward(
            @PathParam("id") String rawId) {
        long id = Validation.parseId(rawId, "Invalid reward id.");
        if (database.execute("DELETE FROM rewards WHERE id = ?", id) == 0) {
            throw new ApiException(404, "Reward not found.");
        }
        return Map.of("message", "Reward deleted.");
    }

    private Map<String, Object> userById(long id) {
        Map<String, Object> user = userByIdOrNull(id);
        if (user == null) {
            throw new ApiException(404, "User not found.");
        }
        return user;
    }

    private Map<String, Object> userByIdOrNull(long id) {
        Map<String, Object> user = database.findOne("SELECT * FROM users WHERE id = ?", id);
        if (user != null) {
            user.put("newsletter_optin", intToBool(user.get("newsletter_optin")));
        }
        return user;
    }

    private Map<String, Object> milestoneResponse(Map<String, Object> milestone, long currentCount) {
        Map<String, Object> response = new LinkedHashMap<>(milestone);
        long targetCount = ((Number) response.get("target_count")).longValue();
        response.put("reached", intToBool(response.get("reached")));
        response.put("current_count", currentCount);
        response.put("progress", Math.min(100, Math.round((currentCount / (double) targetCount) * 100)));
        return response;
    }

    private void requireExists(String sql, long id, String message) {
        requireExists(sql, id, message, 404);
    }

    private void requireExists(String sql, long id, String message, int status) {
        if (database.findOne(sql, id) == null) {
            throw new ApiException(status, message);
        }
    }

    private void requireUpdates(List<String> updates) {
        if (updates.isEmpty()) {
            throw new ApiException(400, "At least one updatable field is required.");
        }
    }

    private RuntimeException mapConflict(RuntimeException exception, String message) {
        if (exception instanceof ApiException apiException && apiException.status() == 409) {
            return new ApiException(409, message);
        }
        return exception;
    }

    private boolean intToBool(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return ((Number) value).intValue() != 0;
    }
}
