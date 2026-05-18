package dev.sakusdev.armatureskin.skin;

import java.nio.file.Path;

public record ArmatureSkinTexture(
        String id,
        String displayName,
        Path path
) {
}
