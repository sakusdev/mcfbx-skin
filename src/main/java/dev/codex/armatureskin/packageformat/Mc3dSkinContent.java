package dev.codex.armatureskin.packageformat;

import java.util.List;
import java.util.Optional;

public record Mc3dSkinContent(Mc3dSkinManifest manifest, byte[] modelBytes, List<Texture> textures) {
    public Mc3dSkinContent {
        modelBytes = modelBytes.clone();
        textures = List.copyOf(textures == null ? List.of() : textures);
    }

    @Override
    public byte[] modelBytes() {
        return modelBytes.clone();
    }

    public Optional<Texture> preferredTexture() {
        String preferred = manifest.defaultTexture();
        if (preferred != null && !preferred.isBlank()) {
            return textures.stream()
                    .filter(texture -> texture.path().equals(preferred))
                    .findFirst()
                    .or(() -> textures.stream().findFirst());
        }
        return textures.stream().findFirst();
    }

    public record Texture(String path, byte[] bytes) {
        public Texture {
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
