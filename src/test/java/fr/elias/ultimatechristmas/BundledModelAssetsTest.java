package fr.elias.ultimateChristmas;

import com.google.gson.JsonObject;
import fr.elias.ultimateChristmas.core.model.BundledModelAssets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BundledModelAssetsTest {
    @TempDir Path directory;

    private Map<String, byte[]> bundle(String content) throws Exception {
        Map<String, byte[]> data = new HashMap<>();
        data.put("rigs/grinch.json", content.getBytes(StandardCharsets.UTF_8));
        JsonObject files = new JsonObject();
        files.addProperty("rigs/grinch.json", hash(data.get("rigs/grinch.json")));
        JsonObject manifest = new JsonObject();
        manifest.addProperty("revision", hash(content.getBytes(StandardCharsets.UTF_8)));
        manifest.add("files", files);
        data.put("model-assets.json", manifest.toString().getBytes(StandardCharsets.UTF_8));
        return data;
    }

    private String hash(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    private BundledModelAssets.Resources resources(Map<String, byte[]> data) {
        return name -> data.containsKey(name) ? new ByteArrayInputStream(data.get(name)) : null;
    }

    @Test
    void contentUpdatesReplaceOldAssetsButPreserveCustomFilesAndRestartEdits() throws Exception {
        var old = bundle("old");
        var fresh = bundle("new");
        BundledModelAssets.install(directory, resources(old));
        Files.writeString(directory.resolve("rigs/custom.json"), "custom");
        BundledModelAssets.install(directory, resources(fresh));
        assertEquals("new", Files.readString(directory.resolve("rigs/grinch.json")));
        assertEquals("custom", Files.readString(directory.resolve("rigs/custom.json")));
        try (var backups = Files.walk(directory.resolve("model-backups"))) {
            Path backup = backups.filter(p -> p.getFileName().toString().equals("grinch.json")).findFirst().orElseThrow();
            assertEquals("old", Files.readString(backup));
        }
        Files.writeString(directory.resolve("rigs/grinch.json"), "local tuning");
        BundledModelAssets.install(directory, resources(fresh));
        assertEquals("local tuning", Files.readString(directory.resolve("rigs/grinch.json")));
        Files.delete(directory.resolve("rigs/grinch.json"));
        BundledModelAssets.install(directory, resources(fresh));
        assertEquals("new", Files.readString(directory.resolve("rigs/grinch.json")));
    }

    @Test
    void invalidBundleCannotAdvanceTheInstalledRevision() throws Exception {
        BundledModelAssets.install(directory, resources(bundle("old")));
        String marker = Files.readString(directory.resolve(".model-assets-installed.json"));
        var broken = bundle("new");
        broken.put("rigs/grinch.json", "corrupt".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> BundledModelAssets.install(directory, resources(broken)));
        assertEquals(marker, Files.readString(directory.resolve(".model-assets-installed.json")));
        assertEquals("old", Files.readString(directory.resolve("rigs/grinch.json")));
    }
}
