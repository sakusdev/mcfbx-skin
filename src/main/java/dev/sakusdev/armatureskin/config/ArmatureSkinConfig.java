package dev.sakusdev.armatureskin.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sakusdev.armatureskin.skin.ArmatureSkin;
import dev.sakusdev.armatureskin.skin.ArmatureSkinManager;
import dev.sakusdev.armatureskin.skin.ArmatureSkinTexture;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record ArmatureSkinConfig(
        boolean enabled,
        boolean localPlayerOnly,
        String selectedSkinId,
        String selectedSkinPath,
        String selectedTextureId,
        String selectedTexturePath,
        String disabledMeshKeys,
        Map<String, String> meshTextureAssignments,
        String fbxPath,
        float scale,
        float yOffset,
        float modelYawOffsetDegrees,
        boolean animationEnabled,
        float animationStrength,
        boolean mirrorVanillaSneak,
        boolean forceOpaqueSkin,
        boolean firstPersonModelHands
) {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public ArmatureSkinConfig(
            boolean enabled,
            boolean localPlayerOnly,
            String selectedSkinId,
            String selectedSkinPath,
            String fbxPath,
            float scale,
            float yOffset,
            boolean mirrorVanillaSneak,
            boolean forceOpaqueSkin
    ) {
        this(enabled, localPlayerOnly, selectedSkinId, selectedSkinPath, "", "", "", Map.of(), fbxPath, scale, yOffset, 0.0F, true, 0.18F, mirrorVanillaSneak, forceOpaqueSkin, false);
    }

    public ArmatureSkinConfig {
        selectedSkinId = selectedSkinId == null ? "" : selectedSkinId;
        selectedSkinPath = selectedSkinPath == null ? "" : selectedSkinPath;
        selectedTextureId = selectedTextureId == null ? "" : selectedTextureId;
        selectedTexturePath = selectedTexturePath == null ? "" : selectedTexturePath;
        disabledMeshKeys = disabledMeshKeys == null ? "" : disabledMeshKeys;
        meshTextureAssignments = cleanAssignments(meshTextureAssignments);
        fbxPath = fbxPath == null ? "" : fbxPath;
        modelYawOffsetDegrees = Float.isFinite(modelYawOffsetDegrees) ? modelYawOffsetDegrees : 0.0F;
        animationStrength = Float.isFinite(animationStrength) ? Math.max(0.0F, Math.min(animationStrength, 1.5F)) : 0.18F;
    }

    public static ArmatureSkinConfig defaults() {
        return new ArmatureSkinConfig(true, true, "", "", "", "", "", Map.of(), "", 1.0F, 0.0F, 0.0F, true, 0.18F, true, true, false);
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
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                ArmatureSkinConfig defaults = defaults();
                return new ArmatureSkinConfig(
                        bool(json, "enabled", defaults.enabled),
                        bool(json, "localPlayerOnly", defaults.localPlayerOnly),
                        string(json, "selectedSkinId", defaults.selectedSkinId),
                        string(json, "selectedSkinPath", defaults.selectedSkinPath),
                        string(json, "selectedTextureId", defaults.selectedTextureId),
                        string(json, "selectedTexturePath", defaults.selectedTexturePath),
                        string(json, "disabledMeshKeys", defaults.disabledMeshKeys),
                        stringMap(json, "meshTextureAssignments"),
                        string(json, "fbxPath", defaults.fbxPath),
                        number(json, "scale", defaults.scale),
                        number(json, "yOffset", defaults.yOffset),
                        number(json, "modelYawOffsetDegrees", defaults.modelYawOffsetDegrees),
                        bool(json, "animationEnabled", defaults.animationEnabled),
                        number(json, "animationStrength", defaults.animationStrength),
                        bool(json, "mirrorVanillaSneak", defaults.mirrorVanillaSneak),
                        bool(json, "forceOpaqueSkin", defaults.forceOpaqueSkin),
                        bool(json, "firstPersonModelHands", defaults.firstPersonModelHands)
                );
            }
        } catch (Exception ex) {
            return defaults();
        }
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsBoolean() : fallback;
    }

    private static float number(JsonObject json, String key, float fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsFloat() : fallback;
    }

    private static Map<String, String> stringMap(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonObject()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        JsonObject object = json.getAsJsonObject(key);
        for (Map.Entry<String, com.google.gson.JsonElement> entry : object.entrySet()) {
            if (!entry.getValue().isJsonNull()) {
                values.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return values;
    }

    private static Map<String, String> cleanAssignments(Map<String, String> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        assignments.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                values.put(key, value);
            }
        });
        return Map.copyOf(values);
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
                selectedTextureId,
                selectedTexturePath,
                "",
                Map.of(),
                fbxPath,
                scale,
                yOffset,
                modelYawOffsetDegrees,
                animationEnabled,
                animationStrength,
                mirrorVanillaSneak,
                forceOpaqueSkin,
                firstPersonModelHands
        );
    }

    public ArmatureSkinConfig withSelectedTexture(ArmatureSkinTexture texture, Path gameDir) {
        Path gamePath = gameDir.toAbsolutePath().normalize();
        Path texturePath = texture.path().toAbsolutePath().normalize();
        Path persistedPath;
        try {
            persistedPath = gamePath.relativize(texturePath);
        } catch (IllegalArgumentException ex) {
            persistedPath = texturePath;
        }
        return new ArmatureSkinConfig(
                enabled,
                localPlayerOnly,
                selectedSkinId,
                selectedSkinPath,
                texture.id(),
                persistedPath.toString().replace('\\', '/'),
                disabledMeshKeys,
                meshTextureAssignments,
                fbxPath,
                scale,
                yOffset,
                modelYawOffsetDegrees,
                animationEnabled,
                animationStrength,
                mirrorVanillaSneak,
                forceOpaqueSkin,
                firstPersonModelHands
        );
    }

    public Path resolveFbxPath(Path gameDir) {
        return ArmatureSkinManager.discover(gameDir, this)
                .resolveSelectedSkin()
                .map(ArmatureSkin::path)
                .orElse(null);
    }

    public Path resolveTexturePath(Path gameDir) {
        return ArmatureSkinManager.discover(gameDir, this)
                .resolveSelectedTexture()
                .map(ArmatureSkinTexture::path)
                .orElse(null);
    }

    public Path resolveConfiguredPath(Path gameDir, String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return null;
        }
        Path path = Path.of(configuredPath);
        return path.isAbsolute() ? path.normalize() : gameDir.resolve(path).normalize();
    }

    public Set<String> disabledMeshKeySet() {
        if (disabledMeshKeys.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(disabledMeshKeys.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isMeshDisabled(String key) {
        return disabledMeshKeySet().contains(key);
    }

    public ArmatureSkinConfig withToggledMesh(String key) {
        Set<String> keys = new java.util.LinkedHashSet<>(disabledMeshKeySet());
        if (!keys.add(key)) {
            keys.remove(key);
        }
        return new ArmatureSkinConfig(
                enabled,
                localPlayerOnly,
                selectedSkinId,
                selectedSkinPath,
                selectedTextureId,
                selectedTexturePath,
                String.join(",", keys),
                meshTextureAssignments,
                fbxPath,
                scale,
                yOffset,
                modelYawOffsetDegrees,
                animationEnabled,
                animationStrength,
                mirrorVanillaSneak,
                forceOpaqueSkin,
                firstPersonModelHands
        );
    }

    public String meshTextureId(String meshKey) {
        if (meshKey == null || meshKey.isBlank()) {
            return "";
        }
        return meshTextureAssignments.getOrDefault(meshKey, "");
    }

    public ArmatureSkinConfig withMeshTexture(String meshKey, String textureId) {
        Map<String, String> assignments = new LinkedHashMap<>(meshTextureAssignments);
        if (meshKey == null || meshKey.isBlank() || textureId == null || textureId.isBlank()) {
            assignments.remove(meshKey);
        } else {
            assignments.put(meshKey, textureId);
        }
        return new ArmatureSkinConfig(
                enabled,
                localPlayerOnly,
                selectedSkinId,
                selectedSkinPath,
                selectedTextureId,
                selectedTexturePath,
                disabledMeshKeys,
                assignments,
                fbxPath,
                scale,
                yOffset,
                modelYawOffsetDegrees,
                animationEnabled,
                animationStrength,
                mirrorVanillaSneak,
                forceOpaqueSkin,
                firstPersonModelHands
        );
    }

    public ArmatureSkinConfig withFirstPersonModelHands(boolean enabled) {
        return new ArmatureSkinConfig(
                this.enabled,
                localPlayerOnly,
                selectedSkinId,
                selectedSkinPath,
                selectedTextureId,
                selectedTexturePath,
                disabledMeshKeys,
                meshTextureAssignments,
                fbxPath,
                scale,
                yOffset,
                modelYawOffsetDegrees,
                animationEnabled,
                animationStrength,
                mirrorVanillaSneak,
                forceOpaqueSkin,
                enabled
        );
    }
}
