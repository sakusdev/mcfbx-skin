package dev.sakusdev.armatureskin.skin;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record ArmatureSkin(
        String id,
        String displayName,
        Path path,
        boolean asciiFbx,
        List<ArmatureSkinTexture> availableTextures
) {
    public ArmatureSkin(String id, String displayName, Path path) {
        this(id, displayName, path, true, List.of());
    }

    public ArmatureSkin {
        availableTextures = List.copyOf(availableTextures == null ? List.of() : availableTextures);
    }

    public Optional<ArmatureSkinTexture> preferredTexture() {
        String fbxBaseName = baseName(path.getFileName().toString());
        return availableTextures.stream()
                .filter(texture -> baseName(texture.path().getFileName().toString()).equalsIgnoreCase(fbxBaseName))
                .findFirst()
                .or(() -> availableTextures.stream().findFirst());
    }

    private static String baseName(String fileName) {
        int extensionStart = fileName.lastIndexOf('.');
        return extensionStart < 0 ? fileName.toLowerCase(Locale.ROOT) : fileName.substring(0, extensionStart).toLowerCase(Locale.ROOT);
    }
}
