package hu.bplusn.portal.storage;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * PostgreSQL tárolás (pl. ingyenes Neon adatbázis), egyetlen táblában.
 * Kevés, ritka írás van, ezért műveletenként nyit kapcsolatot (nincs szükség connection poolra).
 * A Neon/Heroku-stílusú "postgresql://user:pass@host/db?sslmode=require" címet is elfogadja.
 */
public class JdbcStorage implements PortalStorage {

    private final String jdbcUrl;
    private final Properties connectionProperties = new Properties();

    public JdbcStorage(String url, String username, String password) {
        ConnectionInfo info = normalize(url, username, password);
        this.jdbcUrl = info.jdbcUrl();
        if (info.username() != null && !info.username().isBlank()) {
            connectionProperties.setProperty("user", info.username());
        }
        if (info.password() != null && !info.password().isBlank()) {
            connectionProperties.setProperty("password", info.password());
        }
        connectionProperties.setProperty("connectTimeout", "15");
        createSchema();
    }

    public record ConnectionInfo(String jdbcUrl, String username, String password) {
    }

    /** "postgresql://user:pass@host:5432/db?sslmode=require" -> JDBC URL + felhasználó + jelszó. */
    public static ConnectionInfo normalize(String url, String username, String password) {
        String value = url.trim();
        if (value.startsWith("jdbc:")) {
            return new ConnectionInfo(value, username, password);
        }
        if (!value.startsWith("postgres://") && !value.startsWith("postgresql://")) {
            throw new IllegalStateException("Ismeretlen adatbázis-cím formátum (jdbc:postgresql:// vagy postgresql:// kell).");
        }
        URI uri = URI.create(value.replaceFirst("^postgres(ql)?://", "http://"));
        String user = username;
        String pass = password;
        if (uri.getRawUserInfo() != null) {
            String[] parts = uri.getRawUserInfo().split(":", 2);
            if (user == null || user.isBlank()) {
                user = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            }
            if ((pass == null || pass.isBlank()) && parts.length == 2) {
                pass = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        String query = uri.getRawQuery() == null ? "" : Arrays.stream(uri.getRawQuery().split("&"))
                .filter(param -> !param.isBlank() && !param.startsWith("channel_binding="))
                .collect(Collectors.joining("&"));
        String jdbc = "jdbc:postgresql://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "")
                + (uri.getRawPath() == null ? "" : uri.getRawPath()) + (query.isEmpty() ? "" : "?" + query);
        return new ConnectionInfo(jdbc, user, pass);
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, connectionProperties);
    }

    private void createSchema() {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS portal_document (
                        collection VARCHAR(64)  NOT NULL,
                        owner      VARCHAR(128) NOT NULL,
                        content    TEXT         NOT NULL,
                        updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
                        PRIMARY KEY (collection, owner)
                    )""");
        } catch (SQLException exception) {
            throw new IllegalStateException("Az adatbázis nem érhető el vagy a tábla nem hozható létre: "
                    + exception.getMessage(), exception);
        }
    }

    @Override
    public List<String> read(String collection, String owner) {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT content FROM portal_document WHERE collection = ? AND owner = ?")) {
            statement.setString(1, collection);
            statement.setString(2, owner);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return List.of();
                }
                String content = result.getString(1);
                return content == null || content.isEmpty() ? List.of() : new ArrayList<>(List.of(content.split("\n")));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Adatbázis-olvasási hiba: " + exception.getMessage(), exception);
        }
    }

    @Override
    public void write(String collection, String owner, List<String> lines) {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO portal_document (collection, owner, content, updated_at) VALUES (?, ?, ?, now())
                     ON CONFLICT (collection, owner) DO UPDATE SET content = EXCLUDED.content, updated_at = now()""")) {
            statement.setString(1, collection);
            statement.setString(2, owner);
            statement.setString(3, String.join("\n", lines));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Adatbázis-írási hiba: " + exception.getMessage(), exception);
        }
    }

    @Override
    public String description() {
        return "PostgreSQL (" + jdbcUrl.replaceAll("\\?.*$", "") + ")";
    }
}
