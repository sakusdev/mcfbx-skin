package dev.codex.armatureskin.skin;

import dev.codex.armatureskin.config.ArmatureSkinConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public final class ArmatureSkinManager {
    public static final String SKIN_DIRECTORY = "fbx";

    private static final int FBX_HEADER_SAMPLE_BYTES = 64 * 1024;

    private final Path gameDir;
    private final ArmatureSkinConfig config;
    private final List<ArmatureSkin> availableSkins;
    private final List<ArmatureSkinTexture> availableTextures;

    private ArmatureSkinManager(Path gameDir, ArmatureSkinConfig config, List<ArmatureSkin> availableSkins, List<ArmatureSkinTexture> availableTextures) {
        this.gameDir = gameDir;
        this.config = config;
        this.availableSkins = List.copyOf(availableSkins);
        this.availableTextures = List.copyOf(availableTextures);
    }

    public static ArmatureSkinManager discover(Path gameDir, ArmatureSkinConfig config) {
        Path normalizedGameDir = gameDir.toAbsolutePath().normalize();
        Path skinDir = normalizedGameDir.resolve(SKIN_DIRECTORY);
        List<ArmatureSkinTexture> textures = discoverTextures(skinDir);
        List<ArmatureSkin> skins = discoverSkins(skinDir, textures);
        return new ArmatureSkinManager(normalizedGameDir, config, skins, textures);
    }

    public List<ArmatureSkin> availableSkins() {
        return availableSkins;
    }

    public List<ArmatureSkinTexture> availableTextures() {
        return availableTextures;
    }

    public Optional<ArmatureSkin> resolveSelectedSkin() {
        Optional<ArmatureSkin> byId = findById(config.selectedSkinId());
        if (byId.isPresent()) {
            return byId;
        }

        Optional<ArmatureSkin> bySelectedPath = resolveConfiguredPath(config.selectedSkinPath(), "selected-path");
        if (bySelectedPath.isPresent()) {
            return bySelectedPath;
        }

        Optional<ArmatureSkin> byLegacyPath = resolveConfiguredPath(config.fbxPath(), "legacy-path");
        if (byLegacyPath.isPresent()) {
            return byLegacyPath;
        }

        return availableSkins.stream().findFirst();
    }

    public Optional<Path> resolveSelectedPath() {
        return resolveSelectedSkin().map(ArmatureSkin::path);
    }

    public Optional<ArmatureSkinTexture> resolveSelectedTexture() {
        return resolveSelectedSkin().flatMap(this::resolveSelectedTexture);
    }

    public Optional<ArmatureSkinTexture> resolveSelectedTexture(ArmatureSkin skin) {
        Optional<ArmatureSkinTexture> byId = findTextureById(config.selectedTextureId());
        if (byId.isPresent()) {
            return byId;
        }

        Optional<ArmatureSkinTexture> bySelectedPath = resolveConfiguredTexturePath(config.selectedTexturePath(), "selected-texture-path");
        if (bySelectedPath.isPresent()) {
            return bySelectedPath;
        }

        return skin.preferredTexture();
    }

    public Optional<Path> resolveSelectedTexturePath() {
        return resolveSelectedTexture().map(ArmatureSkinTexture::path);
    }

    public Optional<ArmatureSkin> findById(String skinId) {
        if (skinId == null || skinId.isBlank()) {
            return Optional.empty();
        }
        return availableSkins.stream()
                .filter(skin -> skin.id().equalsIgnoreCase(skinId))
                .findFirst();
    }

    public Optional<ArmatureSkinTexture> findTextureById(String textureId) {
        if (textureId == null || textureId.isBlank()) {
            return Optional.empty();
        }
        return availableTextures.stream()
                .filter(texture -> texture.id().equalsIgnoreCase(textureId))
                .findFirst();
    }

    private static List<ArmatureSkin> discoverSkins(Path skinDir, List<ArmatureSkinTexture> textures) {
        try {
            Files.createDirectories(skinDir);
            try (Stream<Path> walk = Files.walk(skinDir)) {
                return walk
                        .filter(Files::isRegularFile)
                        .filter(ArmatureSkinManager::hasFbxExtension)
                        .map(path -> discoveredSkin(skinDir, path, textures))
                        .sorted(Comparator.comparing(ArmatureSkin::id, String.CASE_INSENSITIVE_ORDER))
                        .toList();
            }
        } catch (IOException ex) {
            return List.of();
        }
    }

    private static List<ArmatureSkinTexture> discoverTextures(Path skinDir) {
        try {
            Files.createDirectories(skinDir);
            try (Stream<Path> walk = Files.walk(skinDir)) {
                return walk
                        .filter(Files::isRegularFile)
                        .filter(ArmatureSkinManager::hasTextureExtension)
                        .map(path -> discoveredTexture(skinDir, path))
                        .sorted(Comparator.comparing(ArmatureSkinTexture::id, String.CASE_INSENSITIVE_ORDER))
                        .toList();
            }
        } catch (IOException ex) {
            return List.of();
        }
    }

    private static ArmatureSkin discoveredSkin(Path skinDir, Path path, List<ArmatureSkinTexture> textures) {
        Path normalizedPath = path.toAbsolutePath().normalize();
        String relative = skinDir.relativize(normalizedPath).toString().replace('\\', '/');
        String id = stripFbxExtension(relative);
        return new ArmatureSkin(id, displayName(normalizedPath), normalizedPath, isAsciiFbx(normalizedPath), siblingTextures(normalizedPath, textures));
    }

    private static ArmatureSkinTexture discoveredTexture(Path skinDir, Path path) {
        Path normalizedPath = path.toAbsolutePath().normalize();
        String relative = skinDir.relativize(normalizedPath).toString().replace('\\', '/');
        String id = stripExtension(relative);
        return new ArmatureSkinTexture(id, stripExtension(normalizedPath.getFileName().toString()), normalizedPath);
    }

    private static List<ArmatureSkinTexture> siblingTextures(Path skinPath, List<ArmatureSkinTexture> textures) {
        Path skinParent = skinPath.getParent();
        String skinBaseName = stripExtension(skinPath.getFileName().toString());
        Comparator<ArmatureSkinTexture> byPreference = Comparator
                .comparing((ArmatureSkinTexture texture) -> !stripExtension(texture.path().getFileName().toString()).equalsIgnoreCase(skinBaseName))
                .thenComparing(ArmatureSkinTexture::id, String.CASE_INSENSITIVE_ORDER);
        return textures.stream()
                .filter(texture -> texture.path().getParent() != null && texture.path().getParent().equals(skinParent))
                .sorted(byPreference)
                .toList();
    }

    private Optional<ArmatureSkin> resolveConfiguredPath(String configuredPath, String idPrefix) {
        Path path = config.resolveConfiguredPath(gameDir, configuredPath);
        if (path == null || !Files.isRegularFile(path) || !hasFbxExtension(path)) {
            return Optional.empty();
        }

        Path normalizedPath = path.toAbsolutePath().normalize();
        return availableSkins.stream()
                .filter(skin -> skin.path().toAbsolutePath().normalize().equals(normalizedPath))
                .findFirst()
                .or(() -> Optional.of(new ArmatureSkin(idPrefix + ":" + stripFbxExtension(normalizedPath.getFileName().toString()), displayName(normalizedPath), normalizedPath, isAsciiFbx(normalizedPath), siblingTextures(normalizedPath, availableTextures))));
    }

    private Optional<ArmatureSkinTexture> resolveConfiguredTexturePath(String configuredPath, String idPrefix) {
        Path path = config.resolveConfiguredPath(gameDir, configuredPath);
        if (path == null || !Files.isRegularFile(path) || !hasTextureExtension(path)) {
            return Optional.empty();
        }

        Path normalizedPath = path.toAbsolutePath().normalize();
        return availableTextures.stream()
                .filter(texture -> texture.path().toAbsolutePath().normalize().equals(normalizedPath))
                .findFirst()
                .or(() -> Optional.of(new ArmatureSkinTexture(idPrefix + ":" + stripExtension(normalizedPath.getFileName().toString()), stripExtension(normalizedPath.getFileName().toString()), normalizedPath)));
    }

    private static boolean hasFbxExtension(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".fbx");
    }

    private static boolean hasTextureExtension(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg");
    }

    private static boolean isAsciiFbx(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            byte[] sample = input.readNBytes(FBX_HEADER_SAMPLE_BYTES);
            if (sample.length == 0) {
                return false;
            }
            for (byte value : sample) {
                if (value == 0) {
                    return false;
                }
            }
            String text = new String(sample, StandardCharsets.ISO_8859_1);
            return text.contains("FBXHeaderExtension") && !text.startsWith("Kaydara FBX Binary");
        } catch (IOException ex) {
            return false;
        }
    }

    private static String displayName(Path path) {
        return stripFbxExtension(path.getFileName().toString());
    }

    private static String stripFbxExtension(String value) {
        if (value.toLowerCase(Locale.ROOT).endsWith(".fbx")) {
            return value.substring(0, value.length() - 4);
        }
        return value;
    }

    private static String stripExtension(String value) {
        int extensionStart = value.lastIndexOf('.');
        return extensionStart < 0 ? value : value.substring(0, extensionStart);
    }
}
