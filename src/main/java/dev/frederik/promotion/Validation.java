package dev.frederik.promotion;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.regex.Pattern;

final class Validation {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private Validation() {
    }

    static String optionalText(JsonNode body, String field) {
        requireBody(body);
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new ApiException(400, field + " must be a string or null.");
        }
        return node.asText().trim();
    }

    static String requiredText(JsonNode body, String field, String message) {
        String value = optionalText(body, field);
        if (value == null || value.isBlank()) {
            throw new ApiException(400, message);
        }
        return value;
    }

    static boolean optionalBoolean(JsonNode body, String field, boolean defaultValue) {
        requireBody(body);
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        if (!node.isBoolean()) {
            throw new ApiException(400, field + " must be a boolean.");
        }
        return node.asBoolean();
    }

    static long requiredPositiveLong(JsonNode body, String field, String message) {
        requireBody(body);
        JsonNode node = body.get(field);
        if (node == null || !node.isIntegralNumber() || node.asLong() <= 0) {
            throw new ApiException(400, message);
        }
        return node.asLong();
    }

    static long parseId(String value, String message) {
        try {
            long id = Long.parseLong(value);
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException ignored) {
        }
        throw new ApiException(400, message);
    }

    static void requireEmail(String email) {
        if (email == null || email.isBlank() || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ApiException(400, "A valid email is required.");
        }
    }

    private static void requireBody(JsonNode body) {
        if (body == null || body.isNull() || !body.isObject()) {
            throw new ApiException(400, "Invalid JSON body.");
        }
    }
}
