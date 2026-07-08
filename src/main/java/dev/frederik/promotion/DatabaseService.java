package dev.frederik.promotion;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class DatabaseService {
    private final DataSource dataSource;

    @Inject
    public DatabaseService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    void initialize(@Observes StartupEvent event) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                      id BIGSERIAL PRIMARY KEY,
                      username VARCHAR(120) UNIQUE NOT NULL,
                      email VARCHAR(255) UNIQUE NOT NULL,
                      newsletter_optin BOOLEAN NOT NULL DEFAULT FALSE,
                      created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS platforms (
                      id BIGSERIAL PRIMARY KEY,
                      name VARCHAR(80) UNIQUE NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS registrations (
                      id BIGSERIAL PRIMARY KEY,
                      user_id BIGINT NOT NULL,
                      platform_id BIGINT NOT NULL,
                      created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                      FOREIGN KEY (platform_id) REFERENCES platforms(id) ON DELETE RESTRICT,
                      UNIQUE (user_id, platform_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS milestones (
                      id BIGSERIAL PRIMARY KEY,
                      title VARCHAR(180) NOT NULL,
                      target_count INTEGER NOT NULL CHECK (target_count > 0),
                      reached BOOLEAN NOT NULL DEFAULT FALSE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS rewards (
                      id BIGSERIAL PRIMARY KEY,
                      milestone_id BIGINT NOT NULL,
                      name VARCHAR(160) NOT NULL,
                      description TEXT,
                      image_url VARCHAR(500),
                      FOREIGN KEY (milestone_id) REFERENCES milestones(id) ON DELETE CASCADE
                    )
                    """);
            seed(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not initialize database.", exception);
        }
    }

    private void seed(Connection connection) throws SQLException {
        List<String> platforms = List.of("Windows", "Linux", "Mobile (Android)");
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO platforms (name) VALUES (?) ON CONFLICT (name) DO NOTHING")) {
            for (String platform : platforms) {
                insert.setString(1, platform);
                insert.executeUpdate();
            }
        }

        if (count("SELECT COUNT(*) FROM milestones") > 0) {
            return;
        }

        long firstId = insertAndReturnId(connection,
                "INSERT INTO milestones (title, target_count, reached) VALUES (?, ?, FALSE)",
                "1,000 community registrations", 1000);
        execute(connection,
                "INSERT INTO rewards (milestone_id, name, description, image_url) VALUES (?, ?, ?, ?)",
                firstId,
                "Founder Banner",
                "Exclusive in-game profile banner for early supporters.",
                "https://example.com/rewards/founder-banner.png");

        long secondId = insertAndReturnId(connection,
                "INSERT INTO milestones (title, target_count, reached) VALUES (?, ?, FALSE)",
                "5,000 community registrations", 5000);
        execute(connection,
                "INSERT INTO rewards (milestone_id, name, description, image_url) VALUES (?, ?, ?, ?)",
                secondId,
                "Launch Loot Crate",
                "Free cosmetic crate unlocked for all players at launch.",
                "https://example.com/rewards/launch-loot-crate.png");
    }

    public List<Map<String, Object>> query(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = prepare(connection, sql, params)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                int columnCount = resultSet.getMetaData().getColumnCount();
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(resultSet.getMetaData().getColumnLabel(i), resultSet.getObject(i));
                    }
                    rows.add(row);
                }
                return rows;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public Map<String, Object> findOne(String sql, Object... params) {
        List<Map<String, Object>> rows = query(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public long count(String sql, Object... params) {
        Map<String, Object> row = findOne(sql, params);
        return ((Number) row.values().iterator().next()).longValue();
    }

    public long insertAndReturnId(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection()) {
            return insertAndReturnId(connection, sql, params);
        } catch (SQLException exception) {
            throw mapDatabaseException(exception, "Resource conflict.");
        }
    }

    public int execute(String sql, Object... params) {
        try (Connection connection = dataSource.getConnection()) {
            return execute(connection, sql, params);
        } catch (SQLException exception) {
            throw mapDatabaseException(exception, "Resource conflict.");
        }
    }

    public long registrationCount() {
        return count("SELECT COUNT(*) FROM registrations");
    }

    public void updateReachedMilestones() {
        execute("UPDATE milestones SET reached = TRUE WHERE reached = FALSE AND target_count <= ?", registrationCount());
    }

    public RuntimeException mapDatabaseException(SQLException exception, String conflictMessage) {
        if ("23505".equals(exception.getSQLState())) {
            return new ApiException(409, conflictMessage);
        }
        if ("23503".equals(exception.getSQLState())) {
            return new ApiException(400, "Referenced resource does not exist.");
        }
        return new IllegalStateException(exception);
    }

    private static long insertAndReturnId(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql + " RETURNING id", params);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0;
        }
    }

    private static int execute(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, params)) {
            return statement.executeUpdate();
        }
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object... params) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
        return statement;
    }
}
