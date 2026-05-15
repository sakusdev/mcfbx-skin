package dev.codex.armatureskin.packageformat;

import java.util.List;

public record Mc3dSkinManifest(
        String format,
        int formatVersion,
        String packageId,
        String packageVersion,
        String displayName,
        String author,
        String model,
        String defaultTexture,
        List<String> textures,
        String rig,
        String target,
        String contentSha256
) {
    public List<String> textures() {
        return textures == null ? List.of() : List.copyOf(textures);
    }
}
