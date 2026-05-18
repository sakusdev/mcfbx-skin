package dev.codex.armatureskin.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public record SkinRenderTexture(ResourceLocation location, AlphaMode alphaMode) {
    public SkinRenderTexture {
        alphaMode = alphaMode == null ? AlphaMode.CUTOUT : alphaMode;
    }

    public RenderType renderType() {
        return switch (alphaMode) {
            case TRANSLUCENT -> RenderType.entityTranslucent(location);
            case OPAQUE, CUTOUT -> RenderType.entityCutoutNoCull(location);
        };
    }

    public enum AlphaMode {
        OPAQUE,
        CUTOUT,
        TRANSLUCENT
    }
}
