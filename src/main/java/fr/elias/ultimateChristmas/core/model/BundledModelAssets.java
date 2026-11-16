package fr.elias.ultimateChristmas.core.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Installs matching sources, rigs and pack by content revision, with backups. */
public final class BundledModelAssets {
    private static final String MARKER = ".model-assets-installed.json";
    private BundledModelAssets() { }

    @FunctionalInterface
    public interface Resources {
        InputStream open(String name) throws IOException;
    }

    public static void install(Path directory, Resources resources) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        Files.createDirectories(root);
        byte[] manifestBytes = read(resources, "model-assets.json");
        JsonObject manifest = JsonParser.parseString(new String(manifestBytes, StandardCharsets.UTF_8))
                .getAsJsonObject();
        String revision = manifest.get("revision").getAsString();
        if (!revision.matches("[0-9a-f]{64}")) throw new IOException("Invalid model asset revision");
        Path marker = root.resolve(MARKER);
        JsonObject previous = Files.exists(marker)
                ? JsonParser.parseString(Files.readString(marker)).getAsJsonObject() : null;
        boolean changed = previous == null
                || !revision.equals(previous.get("revision").getAsString());
        JsonObject files = manifest.getAsJsonObject("files");
        Path backup = root.resolve("model-backups").resolve(revision.substring(0, 12));

        // Validate every bundled file before replacing any installed asset.
        for (String name : files.keySet()) {
            destination(root, name);
            if (!sha256(read(resources, name)).equals(files.get(name).getAsString())) {
                throw new IOException("Bundled model checksum mismatch: " + name);
            }
        }
        for (String name : files.keySet()) {
            Path target = destination(root, name);
            if (!changed && Files.isRegularFile(target)) continue;
            byte[] bytes = read(resources, name);
            if (Files.isRegularFile(target) && sha256(Files.readAllBytes(target)).equals(sha256(bytes))) continue;
            backup(target, backup.resolve(name));
            writeAtomically(target, bytes);
        }
        if (changed && previous != null) {
            for (String name : previous.getAsJsonObject("files").keySet()) {
                if (files.has(name)) continue;
                Path target = destination(root, name);
                backup(target, backup.resolve(name));
                Files.deleteIfExists(target);
            }
        }
        // Commit the revision only after every asset was installed successfully.
        writeAtomically(marker, manifestBytes);
    }

    private static Path destination(Path root, String name) throws IOException {
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root) || name.contains("\\")
                || !(name.startsWith("models/") || name.startsWith("rigs/") || name.startsWith("pack/"))) {
            throw new IOException("Invalid bundled asset path: " + name);
        }
        return target;
    }

    private static byte[] read(Resources resources, String name) throws IOException {
        try (InputStream input = resources.open(name)) {
            if (input == null) throw new IOException("Missing bundled asset: " + name);
            return input.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void backup(Path source, Path target) throws IOException {
        if (!Files.isRegularFile(source) || Files.exists(target)) return;
        Files.createDirectories(target.getParent());
        Files.copy(source, target);
    }

    private static void writeAtomically(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".model-", ".tmp");
        try {
            Files.write(temp, bytes);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
