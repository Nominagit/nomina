import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class WipoStateStore {
    private static final Path FILE = Paths.get("scrape-state", "wipo_last_actual_number.txt");

    private WipoStateStore() {
    }

    static String loadOrDefault(String def) {
        try {
            if (!Files.exists(FILE)) return def;
            String v = Files.readString(FILE, StandardCharsets.UTF_8).trim();
            return v.isEmpty() ? def : v;
        } catch (IOException e) {
            return def;
        }
    }

    static void save(String value) {
        if (value == null) return;
        String v = value.trim();
        if (v.isEmpty()) return;

        try {
            Files.createDirectories(FILE.getParent());

            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(tmp, v + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            try {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save WIPO_LAST_ACTUAL_NUMBER to " + FILE.toAbsolutePath(), e);
        }
    }
}