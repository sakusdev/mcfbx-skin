package dev.sakusdev.armatureskin.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Small client-side hook expected from the skin manager/main mod.
 */
public interface ArmatureSkinSelectionApi {
    List<SkinEntry> listSkins();

    Optional<SkinEntry> selectedSkin();

    void selectSkin(SkinEntry skin);

    void reloadSkins();

    void openSkinsFolder();

    default boolean firstPersonModelHandsEnabled() {
        return false;
    }

    default void setFirstPersonModelHandsEnabled(boolean enabled) {
    }

    default List<TextureEntry> listTextures(SkinEntry skin) {
        return List.of();
    }

    default Optional<TextureEntry> selectedTexture(SkinEntry skin) {
        return Optional.empty();
    }

    default void selectTexture(SkinEntry skin, TextureEntry texture) {
    }

    default void assignTextureToMesh(SkinEntry skin, String meshKey, TextureEntry texture) {
    }

    default void toggleMesh(SkinEntry skin, String meshKey) {
    }

    default List<PartEntry> listParts(SkinEntry skin) {
        return List.of();
    }

    default boolean renderPreview(GuiGraphics guiGraphics, int x, int y, int width, int height, float partialTick) {
        return false;
    }

    default void reloadTextures(SkinEntry skin) {
    }

    default void openTexturesFolder(SkinEntry skin) {
        openSkinsFolder();
    }

    record SkinEntry(String id, Component displayName, Path path) {
    }

    record TextureEntry(String id, Component displayName, Path path) {
    }

    record PartEntry(String key, Component displayName, boolean hidden, String textureId) {
        public PartEntry(String key, Component displayName, boolean hidden) {
            this(key, displayName, hidden, "");
        }
    }
}
