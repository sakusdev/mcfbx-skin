package dev.codex.armatureskin.screen;

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

    default List<TextureEntry> listTextures(SkinEntry skin) {
        return List.of();
    }

    default Optional<TextureEntry> selectedTexture(SkinEntry skin) {
        return Optional.empty();
    }

    default void selectTexture(SkinEntry skin, TextureEntry texture) {
    }

    default void reloadTextures(SkinEntry skin) {
    }

    default void openTexturesFolder(SkinEntry skin) {
        openSkinsFolder();
    }

    default Path packageSelectedSkin(SkinEntry skin) {
        throw new UnsupportedOperationException("Packaging is not available.");
    }

    record SkinEntry(String id, Component displayName, Path path) {
    }

    record TextureEntry(String id, Component displayName, Path path) {
    }
}
