package dev.codex.armatureskin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.codex.armatureskin.skin.ArmatureSkin;
import dev.codex.armatureskin.skin.ArmatureSkinManager;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public record ArmatureSkinConfig(
        boolean enabled,
        boolean localPlayerOnly,
        String selectedSkinId,
        String selectedSkinPath,
        String fbxPath,
        float scale,
        float yOffset,
        boolean mirrorVanillaSneak
) {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static ArmatureSkinConfig defaults() {
        return new ArmatureSkinConfig(true, true, "", "", "", 0.01F, 0.0F, true);
    }

    public static ArmatureSkinConfig loadOrCreate(Path gameDir) {
        Path configPath = gameDir.resolve("config").resolve("armature-fbx-skin.json");
        try {
            Files.createDirectories(configPath.getParent());
            if (Files.notExists(configPath)) {
                ArmatureSkinConfig defaults = defaults();
                try (Writer writer = Files.newBufferedWriter(configPath)) {
                    GSON.toJson(defaults, writer);
                }
                return defaults;
            }
            try (Reader reader = Files.newBufferedReader(configPath)) {
                ArmatureSkinConfig config = GSON.fromJson(reader, ArmatureSkinConfig.class);
                return config == null ? defaults() : config;
            }
        } catch (IOException ex) {
            return defaults();
        }
    }

    public void save(Path gameDir) throws IOException {
        Path configPath = gameDir.resolve("config").resolve("armature-fbx-skin.json");
        Files.createDirectories(configPath.getParent());
        try (Writer writer = Files.newBufferedWriter(configPath)) {
            GSON.toJson(this, writer);
        }
    }

    public ArmatureSkinConfig withSelectedSkin(ArmatureSkin skin, Path gameDir) {
        Path gamePath = gameDir.toAbsolutePath().normalize();
        Path skinPath = skin.path().toAbsolutePath().normalize();
        Path persistedPath;
        try {
            persistedPath = gamePath.relativize(skinPath);
        } catch (IllegalArgumentException ex) {
            persistedPath = skinPath;
        }
        return new ArmatureSkinConfig(
                enabled,
                localPlayerOnly,
                skin.id(),
                persistedPath.toString().replace('\\', '/'),
                fbxPath,
                scale,
                yOffset,
                mirrorVanillaSneak
        );
    }

    public Path resolveFbxPath(Path gameDir) {
        return ArmatureSkinManager.discover(gameDir, this)
                .resolveSelectedSkin()
                .map(ArmatureSkin::path)
                .orElse(null);
    }

    public Path resolveConfiguredPath(Path gameDir, String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return null;
        }
        Path path = Path.of(configuredPath);
        return path.isAbsolute() ? path.normalize() : gameDir.resolve(path).normalize();
    }
}
