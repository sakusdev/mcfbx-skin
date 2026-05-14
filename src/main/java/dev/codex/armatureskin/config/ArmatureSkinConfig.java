package dev.codex.armatureskin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public record ArmatureSkinConfig(
        boolean enabled,
        boolean localPlayerOnly,
        String fbxPath,
        float scale,
        float yOffset,
        boolean mirrorVanillaSneak
) {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static ArmatureSkinConfig defaults() {
        return new ArmatureSkinConfig(true, true, "models/player_ascii.fbx", 0.01F, 0.0F, true);
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

    public Path resolveFbxPath(Path gameDir) {
        if (fbxPath == null || fbxPath.isBlank()) {
            return null;
        }
        Path path = Path.of(fbxPath);
        return path.isAbsolute() ? path.normalize() : gameDir.resolve(path).normalize();
    }
}
