import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

public final class AipaStateStore {

    private static final Path DIR = Paths.get("scrape-state");
    private static final Path FILE = DIR.resolve("aipa_last_suffix.properties");

    private AipaStateStore() {}

    public static int loadOrDefault(String prefix, int defaultValue) {
        Properties p = loadProps();
        String v = p.getProperty(prefix);
        if (v == null || v.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static void save(String prefix, int suffix) {
        try {
            Files.createDirectories(DIR);

            Properties p = loadProps();
            p.setProperty(prefix, Integer.toString(suffix));

            Path tmp = FILE.resolveSibling(FILE.getFileName().toString() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                p.store(out, "AIPA last successful suffix per prefix");
            }

            // атомарная замена (если файловая система позволяет)
            try {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (IOException e) {
            System.err.println("WARN: failed to save AIPA state: " + e.getMessage());
        }
    }

    private static Properties loadProps() {
        Properties p = new Properties();
        if (Files.exists(FILE)) {
            try (InputStream in = Files.newInputStream(FILE)) {
                p.load(in);
            } catch (IOException ignored) {
            }
        }
        return p;
    }
}
