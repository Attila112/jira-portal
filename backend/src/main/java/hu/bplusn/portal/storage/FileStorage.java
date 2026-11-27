package hu.bplusn.portal.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Helyi fájlos tárolás: {dataDir}/{gyűjtemény}/{tulajdonos}.tsv (fejlesztéshez, saját szerverhez). */
public class FileStorage implements PortalStorage {

    private final Path root;

    public FileStorage(Path root) {
        this.root = root;
    }

    @Override
    public synchronized List<String> read(String collection, String owner) {
        Path file = root.resolve(collection).resolve(owner + ".tsv");
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Az adat nem olvasható: " + collection, exception);
        }
    }

    @Override
    public synchronized void write(String collection, String owner, List<String> lines) {
        try {
            Path directory = root.resolve(collection);
            Files.createDirectories(directory);
            Path temp = Files.createTempFile(directory, collection, ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            Files.move(temp, directory.resolve(owner + ".tsv"), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new UncheckedIOException("Az adat nem írható: " + collection, exception);
        }
    }

    @Override
    public String description() {
        return "fájl (" + root.toAbsolutePath() + ")";
    }
}
