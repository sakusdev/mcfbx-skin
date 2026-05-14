package dev.codex.armatureskin.skin;

import java.nio.file.Path;

public record ArmatureSkin(
        String id,
        String displayName,
        Path path
) {
}
