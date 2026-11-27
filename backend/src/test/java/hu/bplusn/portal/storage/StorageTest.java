package hu.bplusn.portal.storage;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageTest {

    @Test
    void neonStyleUrlIsConvertedToJdbc() {
        JdbcStorage.ConnectionInfo info = JdbcStorage.normalize(
                "postgresql://portal_owner:p%40ss@ep-cool-1234.eu-central-1.aws.neon.tech/portal?sslmode=require&channel_binding=require",
                null, null);
        assertEquals("jdbc:postgresql://ep-cool-1234.eu-central-1.aws.neon.tech/portal?sslmode=require", info.jdbcUrl());
        assertEquals("portal_owner", info.username());
        assertEquals("p@ss", info.password());
    }

    @Test
    void jdbcUrlIsKept() {
        JdbcStorage.ConnectionInfo info = JdbcStorage.normalize("jdbc:postgresql://localhost:5432/portal", "u", "p");
        assertEquals("jdbc:postgresql://localhost:5432/portal", info.jdbcUrl());
        assertEquals("u", info.username());
    }

    @Test
    void fileStorageRoundTrip() throws Exception {
        Path dir = Files.createTempDirectory("portal-storage");
        FileStorage storage = new FileStorage(dir);
        assertEquals(List.of(), storage.read("leaves", "abc"));
        storage.write("leaves", "abc", List.of("2026-11-16\tVACATION\t", "2026-11-17\tSICK\tláz"));
        assertEquals(List.of("2026-11-16\tVACATION\t", "2026-11-17\tSICK\tláz"), storage.read("leaves", "abc"));
    }
}
