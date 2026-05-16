package dev.codex.armatureskin.packageformat;

import java.util.List;
import java.util.Optional;

public record Mc3dSkinContent(Mc3dSkinManifest manifest, byte[] modelBytes, List<Texture> textures) {
    public Mc3dSkinContent {
        textures = List.copyOf(textures == null ? List.of() : textures);
    }

    public Optional<Texture> preferredTexture() {
        String preferred = manifest == null ? "" : manifest.preferredTexture();
        if (preferred != null && !preferred.isBlank()) {
            return textures.stream()
                    .filter(texture -> texture.path().equals(preferred))
                    .findFirst()
                    .or(() -> textures.stream().findFirst());
        }
        return textures.stream().findFirst();
    }

    public record Texture(String path, byte[] bytes) {
    }
}
