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

    record SkinEntry(String id, Component displayName, Path path) {
    }
}
