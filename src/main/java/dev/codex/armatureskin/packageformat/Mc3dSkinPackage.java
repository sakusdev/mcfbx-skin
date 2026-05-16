package dev.codex.armatureskin.packageformat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.codex.armatureskin.skin.ArmatureSkin;
import dev.codex.armatureskin.skin.ArmatureSkinTexture;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class Mc3dSkinPackage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Mc3dSkinPackage() {
    }

    public static Path writePlain(Path gameDir, ArmatureSkin skin, List<ArmatureSkinTexture> textures, ArmatureSkinTexture preferredTexture) throws IOException {
        if (skin == null || skin.packageSkin()) {
            throw new IOException("Select an FBX skin before packaging.");
        }
        Path skinDir = gameDir.toAbsolutePath().normalize().resolve("fbx");
        Files.createDirectories(skinDir);
        String baseName = stripExtension(skin.path().getFileName().toString());
        Path output = uniquePath(skin.path().getParent() == null ? skinDir : skin.path().getParent(), baseName, ".mc3dskin");

        List<ArmatureSkinTexture> packageTextures = new ArrayList<>(textures == null ? List.of() : textures);
        if (preferredTexture != null && preferredTexture.path() != null && packageTextures.stream().noneMatch(texture -> texture.id().equals(preferredTexture.id()))) {
            packageTextures.add(preferredTexture);
        }

        List<TextureEntry> textureEntries = new ArrayList<>();
        for (ArmatureSkinTexture texture : packageTextures) {
            if (texture.path() != null && Files.isRegularFile(texture.path())) {
                textureEntries.add(new TextureEntry(texture, "textures/" + texture.path().getFileName().toString()));
            }
        }
        String preferredPath = preferredTexture == null ? "" : textureEntries.stream()
                .filter(entry -> entry.texture().id().equals(preferredTexture.id()))
                .map(TextureEntry::entryPath)
                .findFirst()
                .orElse("");
        Mc3dSkinManifest manifest = new Mc3dSkinManifest(
                "mc3dskin",
                1,
                sanitizePackageId(baseName),
                "1.0.0",
                baseName,
                "Sakutarooo",
                "model/" + skin.path().getFileName().toString(),
                textureEntries.stream().map(TextureEntry::entryPath).toList(),
                preferredPath
        );

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(output), StandardCharsets.UTF_8)) {
            putBytes(zip, "manifest.json", GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));
            putBytes(zip, manifest.model(), Files.readAllBytes(skin.path()));
            for (TextureEntry entry : textureEntries) {
                putBytes(zip, entry.entryPath(), Files.readAllBytes(entry.texture().path()));
            }
        }
        return output;
    }

    public static Mc3dSkinContent read(Path path) throws IOException {
        Map<String, byte[]> entries = unzip(path);
        byte[] manifestBytes = entries.get("manifest.json");
        if (manifestBytes == null) {
            throw new IOException("mc3dskin package is missing manifest.json.");
        }
        Mc3dSkinManifest manifest = GSON.fromJson(new String(manifestBytes, StandardCharsets.UTF_8), Mc3dSkinManifest.class);
        if (manifest == null || manifest.model() == null || manifest.model().isBlank()) {
            throw new IOException("mc3dskin manifest is missing model.");
        }
        byte[] modelBytes = entries.get(normalizeEntry(manifest.model()));
        if (modelBytes == null) {
            throw new IOException("mc3dskin package is missing model: " + manifest.model());
        }
        List<Mc3dSkinContent.Texture> textures = new ArrayList<>();
        for (String texture : manifest.textures()) {
            byte[] bytes = entries.get(normalizeEntry(texture));
            if (bytes != null) {
                textures.add(new Mc3dSkinContent.Texture(normalizeEntry(texture), bytes));
            }
        }
        return new Mc3dSkinContent(manifest, modelBytes, textures);
    }

    private static Map<String, byte[]> unzip(Path path) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long totalBytes = 0L;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = normalizeEntry(entry.getName());
                if (name.startsWith("../") || name.contains("/../")) {
                    throw new IOException("Unsafe mc3dskin entry: " + entry.getName());
                }
                byte[] bytes = zip.readAllBytes();
                totalBytes += bytes.length;
                if (entries.size() > 128 || totalBytes > 100L * 1024L * 1024L) {
                    throw new IOException("mc3dskin package exceeds safety limits.");
                }
                entries.put(name, bytes);
            }
        }
        return entries;
    }

    private static void putBytes(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(normalizeEntry(name));
        entry.setTime(Instant.now().toEpochMilli());
        zip.putNextEntry(entry);
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            input.transferTo(zip);
        }
        zip.closeEntry();
    }

    private static Path uniquePath(Path directory, String baseName, String extension) {
        Path candidate = directory.resolve(baseName + extension);
        if (Files.notExists(candidate)) {
            return candidate;
        }
        int index = 2;
        while (true) {
            candidate = directory.resolve(baseName + "-" + index + extension);
            if (Files.notExists(candidate)) {
                return candidate;
            }
            index++;
        }
    }

    private static String sanitizePackageId(String value) {
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
    }

    private static String stripExtension(String fileName) {
        int extensionStart = fileName.lastIndexOf('.');
        return extensionStart < 0 ? fileName : fileName.substring(0, extensionStart);
    }

    private static String normalizeEntry(String name) {
        return name.replace('\\', '/');
    }

    private record TextureEntry(ArmatureSkinTexture texture, String entryPath) {
    }
}
